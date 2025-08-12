object RuleStatus {
    const val ACTIVE = "active"
    const val DISABLED = "disabled"
}
fun uiToDbStatus(on: Boolean) = if (on) RuleStatus.ACTIVE else RuleStatus.DISABLED
fun dbToUiChecked(db: String?) = db == RuleStatus.ACTIVE