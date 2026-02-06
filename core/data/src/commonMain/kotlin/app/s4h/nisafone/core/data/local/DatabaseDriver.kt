package app.s4h.nisafone.core.data.local

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: DatabaseDriverFactory): NisafoneDatabase {
    val driver = driverFactory.createDriver()
    return NisafoneDatabase(driver)
}
