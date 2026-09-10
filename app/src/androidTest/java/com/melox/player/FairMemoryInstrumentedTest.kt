package com.melox.player

import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.melox.player.memory.FAIR_MEMORY_KILL
import com.melox.player.memory.FairMemoryNotification
import com.melox.player.ui.component.library.canRetainArtworkInMemory
import com.melox.player.ui.component.library.loadCachedArtworkDerivative
import com.melox.player.ui.component.library.trimArtworkMemoryCache
import com.melox.player.ui.component.playback.trimBlurredArtworkMemoryCache
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FairMemoryInstrumentedTest {
    @Test
    fun trimmingPreservesDisplayedBitmapAndDiskCache() = runBlocking {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(baseContext.cacheDir, "fair-memory-test-${System.nanoTime()}")
        val context = object : ContextWrapper(baseContext) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = directory
        }
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        try {
            val retained = requireNotNull(loadCachedArtworkDerivative(context, directory.name) { bitmap })
            val cachedFiles = directory.walkTopDown().filter(File::isFile).toList()
            assertTrue(cachedFiles.isNotEmpty())
            trimArtworkMemoryCache()
            trimBlurredArtworkMemoryCache()
            assertFalse(retained.isRecycled)
            assertFalse(canRetainArtworkInMemory())
            assertTrue(cachedFiles.all(File::exists))
            val reloaded = loadCachedArtworkDerivative(context, directory.name) {
                error("Disk cache should survive memory trimming")
            }
            assertNotNull(reloaded)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun callbackUsesDocumentedParcelOrderAndOneWayTransaction() {
        val received = mutableListOf<Int>()
        val callback = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                assertEquals(IBinder.FIRST_CALL_TRANSACTION, code)
                assertEquals(IBinder.FLAG_ONEWAY, flags)
                assertNull(reply)
                repeat(3) { received.add(data.readInt()) }
                assertNotNull(data.readBundle())
                assertEquals(0, data.dataAvail())
                return true
            }
        }
        val intent = Intent(FAIR_MEMORY_KILL).putExtra("common", Bundle().apply {
            putString("action", "kill")
            putInt("notifyType", 2000)
            putInt("notifyId", -42)
            putBinder("callback", callback)
        })
        requireNotNull(FairMemoryNotification.from(intent)).reply(0)
        assertEquals(listOf(2000, -42, 0), received)
    }

    @Test
    fun incompletePayloadDoesNotProduceARequest() {
        assertNull(FairMemoryNotification.from(Intent(FAIR_MEMORY_KILL)))
        val common = Bundle().apply {
            putString("action", "kill")
            putInt("notifyType", 1000)
            putInt("notifyId", 1)
        }
        assertNull(FairMemoryNotification.from(Intent(FAIR_MEMORY_KILL).putExtra("common", common)))
        common.putBinder("callback", Binder())
        common.remove("notifyId")
        assertNull(FairMemoryNotification.from(Intent(FAIR_MEMORY_KILL).putExtra("common", common)))
        common.putString("notifyId", "invalid")
        assertNull(FairMemoryNotification.from(Intent(FAIR_MEMORY_KILL).putExtra("common", common)))
    }
}
