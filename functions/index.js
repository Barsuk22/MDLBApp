const functions = require('firebase-functions');
const admin     = require('firebase-admin');
admin.initializeApp();
const db = admin.firestore();

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
exports.updateHabits = functions.https.onRequest(async (req, res) => {
  // 1) Простейшая авторизация
  const secret = req.query.secret;
  const cfg    = functions.config().jobs || {};
  if (!secret || secret !== cfg.secret) {
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