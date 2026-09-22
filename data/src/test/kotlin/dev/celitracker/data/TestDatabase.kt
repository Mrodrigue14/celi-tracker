package dev.celitracker.data

import androidx.room.Room
import java.io.File

/** A real temporary database, not a fake: the tests cover the actual Room round trip. */
internal class TestDatabase : AutoCloseable {
    val file: File = File.createTempFile("celi-tracker-test", ".db")
    private val database = configureDatabase(Room.databaseBuilder<CeliTrackerDatabase>(name = file.absolutePath))
    val repository = Repository(database)

    /** Keeps the file, to read it back with a raw driver. */
    fun closeRoom() = database.close()

    override fun close() {
        database.close()
        file.delete()
    }
}
