const functions   = require('firebase-functions');
const admin       = require('firebase-admin');
admin.initializeApp();
const db = admin.firestore();

const { onRequest }   = require('firebase-functions/v2/https');
const { defineString }= require('firebase-functions/params');

const JOBS_SECRET = defineString('sGZsS0TGKkM8VStZKOtZCjk1wK0Cj6k/fNhX2/fL8M0=');

/** Добавляем N дней к дате (UTC) */
function addDays(date, n) {
  const d = new Date(date.getTime());
  d.setUTCDate(d.getUTCDate() + n);
  return d;
}

/**
 * Вычисляет nextDueDate по схеме daily/weekly/once.
 * habit: объект из Firestore со свойствами:
 *   - repeat: "daily" | "weekly" | "once"
 *   - daysOfWeek: ["MONDAY","WEDNESDAY",…] или null
 *   - oneTimeDate: "YYYY-MM-DD" или null
 *   - deadline: "HH:mm" или null
 * todayDate: JS Date с временем 00:00 UTC (граница дня для расчётов)
 */
function computeNextDueDateForHabit(habit, todayDate) {
  const { repeat, daysOfWeek, oneTimeDate, deadline } = habit;

  // Парсим дедлайн "HH:mm" → минуты от 0 до 1440
  const parseDeadline = d => {
    if (!d) return null;
    const [h, m] = d.split(':').map(Number);
    return h * 60 + m;
  };
  const dlMin = parseDeadline(deadline);

  // Текущее время UTC в минутах
  const now = new Date();
  const nowMin = now.getUTCHours() * 60 + now.getUTCMinutes();

  if (repeat === 'daily') {
    // Если дедлайн уже прошёл — следующий день, иначе сегодня
    if (dlMin !== null && nowMin > dlMin) {
      return addDays(todayDate, 1);
    }
    return todayDate;
  }

  if (repeat === 'weekly' && Array.isArray(daysOfWeek) && daysOfWeek.length) {
    const dowMap = {
      SUNDAY: 0, MONDAY: 1, TUESDAY: 2,
      WEDNESDAY: 3, THURSDAY: 4, FRIDAY: 5, SATURDAY: 6
    };
    const wanted = daysOfWeek
      .map(d => dowMap[d.toUpperCase()])
      .filter(n => n !== undefined);

    // Ищем ближайший день недели от todayDate включительно
    for (let i = 0; i < 7; i++) {
      const cand = addDays(todayDate, i);
      if (wanted.includes(cand.getUTCDay())) {
        return cand;
      }
    }
    return todayDate;
  }

  if (repeat === 'once' && typeof oneTimeDate === 'string') {
    const parts = oneTimeDate.split('-').map(Number);
    if (parts.length === 3) {
      return new Date(Date.UTC(parts[0], parts[1] - 1, parts[2]));
    }
  }

  // Фоллбэк — возвращаем todayDate
  return todayDate;
}

/** Сбрасывает streak, если предыдущий nextDueDate (из БД) < todayDate и completedToday==false */
function shouldResetStreak(habit, todayDate) {
  if (!habit.nextDueDate || habit.completedToday) return false;
  const prev = habit.nextDueDate.toDate(); // Firestore Timestamp → JS Date
  return prev.getTime() < todayDate.getTime();
}

/**
 * HTTP-функция, которая обновляет привычки всех Мамочек.
 * Вызывается внешним кроном (GitHub Actions).
 */

exports.updateHabits = onRequest(async (req, res) => {
  const secret = req.query.secret;
  if (!secret || secret !== JOBS_SECRET.value()) {
    return res.status(403).send('Forbidden');
  }

  // 2) Берём всех Мамочек
  const momsSnap = await db.collection('users')
    .where('role','==','Mommy')
    .get();

  const nowUtc = new Date();
  const batch = db.batch();

  // 3) Для каждой Мамочки считаем её «сегодня» и обновляем привычки
  for (const momDoc of momsSnap.docs) {
    const { timezone } = momDoc.data();
    // Преобразуем текущий UTC в локальную дату мамы:
    const [Y,M,D] = nowUtc
      .toLocaleDateString('en-CA', { timeZone: timezone })
      .split('-').map(Number);
    const todayDate = new Date(Date.UTC(Y, M-1, D));

    // 4) Читаем её привычки
    const habitsSnap = await db.collection('habits')
      .where('mommyUid','==', momDoc.id)
      .get();

    // 5) Считаем новые даты и сбрасываем флаги
    habitsSnap.docs.forEach(hDoc => {
      const h     = hDoc.data();
      const next  = computeNextDueDateForHabit(h, todayDate);
      const reset = shouldResetStreak(h, todayDate);
      batch.update(hDoc.ref, {
        nextDueDate: admin.firestore.Timestamp.fromDate(next),
        completedToday: false,
        currentStreak: reset ? 0 : h.currentStreak
      });
    });
  }

  // 6) Коммитим батч
  await batch.commit();
  return res.send('OK');
});

/** Пуш при создании звонка: шлём data-пакет "type=call" получателю */
exports.notifyOnCallCreate = functions.firestore
  .document('chats/{tid}/calls/{cid}')
  .onCreate(async (snap) => {
    const call = snap.data();
    if (!call || call.state !== 'ringing') return null;

    const { calleeUid, callerUid } = call;

    const callerDoc  = await db.collection('users').doc(callerUid).get();
    const callerName = callerDoc.get('displayName') || 'Мамочка';

    const userDoc = await db.collection('users').doc(calleeUid).get();
    const tokens  = userDoc.get('fcmTokens') || [];
    if (!tokens.length) return null;

    const message = {
      tokens,
      data: { type: 'call', fromUid: callerUid, fromName: callerName },
      android: { priority: 'HIGH', ttl: '0s' } // немедленно
    };

    const resp = await admin.messaging().sendEachForMulticast(message);

    // чистим тухлые токены (как у тебя)
    const bad = [];
    resp.responses.forEach((r,i) => {
      if (!r.success) {
        const err = String(r.error?.code || '');
        if (err.includes('registration-token-not-registered')) bad.push(tokens[i]);
      }
    });
    if (bad.length) {
      await db.collection('users').doc(calleeUid)
        .update({ fcmTokens: admin.firestore.FieldValue.arrayRemove(...bad) });
    }
    return null;
  });

  exports.notifyOnCallEndQuick = functions.firestore
    .document('chats/{tid}/calls/{cid}')
    .onUpdate(async (change) => {
      const before = change.before.data();
      const after  = change.after.data();
      if (!before || !after) return null;

      // интересует только переход ringing -> ended (без connected/answer)
      const endedQuickly = (before.state === 'ringing') &&
                           (after.state === 'ended') &&
                           !after.answer && !after.answerEnc;

      if (!endedQuickly) return null;

      const calleeUid = after.calleeUid;
      const userDoc = await db.collection('users').doc(calleeUid).get();
      const tokens  = userDoc.get('fcmTokens') || [];
      if (!tokens.length) return null;

      await admin.messaging().sendEachForMulticast({
        tokens,
        data: { type: 'call_cancel', fromUid: after.callerUid },
        android: { priority: 'HIGH', ttl: '0s' }
      });
      return null;
    });