package com.example.readpdf

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "ReadPdf"
    }

    private lateinit var statusText: TextView
    private var tts: TextToSpeech? = null

    private val pickPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            statusText.text = getString(R.string.status_loading)
            Log.d(TAG, "PDF seleccionado: $uri")
            readPdf(uri)
        } else {
            Log.w(TAG, "No se seleccionó ningún PDF.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        PDFBoxResourceLoader.init(applicationContext)

        statusText = findViewById(R.id.statusText)
        val pickButton = findViewById<Button>(R.id.pickPdfButton)
        pickButton.setOnClickListener {
            pickPdf.launch(arrayOf("application/pdf"))
        }

        tts = TextToSpeech(this, this)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale("es", "ES")
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("es"))
            }
        }
    }

    private fun readPdf(uri: Uri) {
        Log.d(TAG, "Iniciando lectura del PDF: $uri")
        val tempFile = copyToCache(contentResolver, uri) ?: run {
            statusText.text = getString(R.string.status_error)
            Log.e(TAG, "Error al copiar el PDF al cache.")
            return
        }

        val text = try {
            val document = PDDocument.load(tempFile)
            val stripper = PDFTextStripper()
            val extractedText = stripper.getText(document).trim()
            document.close()
            extractedText
        } catch (exception: Exception) {
            Log.e(TAG, "Error al procesar el PDF.", exception)
            statusText.text = getString(R.string.status_error)
            return
        }

        if (text.isBlank()) {
            statusText.text = getString(R.string.status_error)
            Log.w(TAG, "El PDF no contiene texto legible.")
            return
        }

        Log.d(TAG, "Texto extraído, iniciando TTS.")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pdf_read")
        statusText.text = getString(R.string.status_done)
    }

    private fun copyToCache(contentResolver: ContentResolver, uri: Uri): File? {
        val cacheFile = File(cacheDir, "selected.pdf")
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            cacheFile
        } catch (exception: Exception) {
            Log.e(TAG, "Error al copiar el archivo a cache.", exception)
            null
        }
    }
}
