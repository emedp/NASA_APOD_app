package migueldp.apod

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CalendarView
import android.widget.EditText
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.edit
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.ImageRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences

    private val API_BASE_URL = "https://api.nasa.gov/planetary/apod"
    private val API_KEY = "?api_key="
    private var apiKey: String = ""
    private val API_DATE = "&date="

    private lateinit var translator: Translator
    private lateinit var requestQueue: RequestQueue

    // UI
    private lateinit var bOpenInBrowser: Button
    private lateinit var bDate: Button
    private lateinit var ivInfo: ImageView
    private lateinit var calendarView: CalendarView
    private lateinit var tvVersion: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvExplanation: TextView
    private lateinit var tvCopyright: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var ivPicture: ImageView
    private lateinit var vvVideo: VideoView

    private lateinit var mediaUrl: String
    private lateinit var mediaUrlHD: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // VAR AND VAL HELPERS
        val today = Calendar.getInstance()
        var selectedDay: Int
        var selectedMonth: Int
        var selectedYear: Int

        // UI BINDING
        bOpenInBrowser = findViewById(R.id.b_website)
        bDate = findViewById(R.id.b_date)
        ivInfo = findViewById(R.id.iv_info)
        calendarView = findViewById(R.id.calendarView)
        tvVersion = findViewById(R.id.tv_version)
        tvTitle = findViewById(R.id.tv_title)
        tvExplanation = findViewById(R.id.tv_explanation)
        tvCopyright = findViewById(R.id.tv_copyright)
        progressBar = findViewById(R.id.progress_bar)
        ivPicture = findViewById(R.id.picture)
        vvVideo = findViewById(R.id.video)

        // FIRSTS
        sharedPreferences = getSharedPreferences("APOD_PREFERENCES", Context.MODE_PRIVATE)
        apiKey = sharedPreferences.getString("API_KEY", "").toString()
        requestQueue = Volley.newRequestQueue(this)
        showUI(false)

        if (apiKey == "")
            requestAPIKey()
        else
            loadUI()

        // UI LOGIC
        tvTitle.setOnClickListener {
            if (tvExplanation.visibility == View.GONE)
                tvExplanation.visibility = View.VISIBLE
            else
                tvExplanation.visibility = View.GONE
        }

        bDate.setOnClickListener {
            if (calendarView.visibility == View.GONE)
                calendarView.visibility = View.VISIBLE
            else
                calendarView.visibility = View.GONE
        }

        ivInfo.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setIcon(AppCompatResources.getDrawable(this, R.drawable.baseline_info_24))
                .setTitle(getString(R.string.info))
                .setMessage(getString(R.string.instruction))
                .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
                .show()
        }

        bOpenInBrowser.setOnClickListener {
            val openUrlInBrowser = Intent(Intent.ACTION_VIEW, Uri.parse("https://apod.nasa.gov/apod/"))
            startActivity(openUrlInBrowser)
        }

        calendarView.maxDate = today.timeInMillis
        calendarView.minDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse("1995-06-16").time
        calendarView.setOnDateChangeListener { _, year, month, day ->
            selectedYear = year
            selectedMonth = month + 1
            selectedDay = day
            Log.d("CALENDAR.DATE_CHANGED", "yyyy/MM/dd: $selectedYear/$selectedMonth/$selectedDay")
            // view.date = SimpleDateFormat("yyyy-MM-dd").parse("$year-${month + 1}-$day").time
            showUI(false)
            requestJson(year, month + 1, day)
        }

        // TRANSLATION FEATURE BLOCK CODE
        // https://developers.google.com/ml-kit/language/translation/android?hl=es-419
        translator = Translation.getClient(TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build())

        val downloadConditions = DownloadConditions.Builder().requireWifi().build()

        translator.downloadModelIfNeeded(downloadConditions)
            .addOnSuccessListener {
                // Model downloaded successfully. Okay to start translating.

                // https://google.github.io/volley/simple.html
            }
            .addOnFailureListener { exception ->
                // Model could not be downloaded or other internal error.

                Log.d("TRANSLATOR_EXCEPTION", exception.toString())
            }
    }

    private fun requestJson (year: Int, month: Int, day: Int) {
        val requestURL = "$API_BASE_URL$API_KEY$apiKey$API_DATE$year-$month-$day"
        requestQueue.add(JsonObjectRequest(Request.Method.GET, requestURL, null,
            { response ->
                Log.d("RESPONSE_JSON", response.toString())
                val version = if (response.has("service_version")) response.getString("service_version") else ""
                val date = if (response.has("date")) response.getString("date") else ""
                val title = if (response.has("title")) response.getString("title") else ""
                val explanation = if (response.has("explanation")) response.getString("explanation") else ""
                val copyright = if (response.has("copyright")) response.getString("copyright") else ""
                val mediaType = if (response.has("media_type")) response.getString("media_type") else ""
                mediaUrl = if (response.has("url")) response.getString("url") else ""
                mediaUrlHD = if (response.has("hdurl")) response.getString("hdurl") else ""

                translator.translate(title)
                    .addOnSuccessListener { translatedText -> tvTitle.text = translatedText }
                    .addOnFailureListener { exception -> Log.d("TRANSLATION_EXCEPTION", exception.toString()) }
                translator.translate(explanation)
                    .addOnSuccessListener { translatedText -> tvExplanation.text = translatedText }
                    .addOnFailureListener { exception -> Log.d("TRANSLATION_EXCEPTION", exception.toString()) }

                tvVersion.text = getString(R.string.version_title, version)
                bDate.text = date
                if (copyright != "")
                    tvCopyright.text = getString(R.string.copyright_title, copyright.replace("\n", ""))
                else
                    tvCopyright.text = null

                Log.d("MEDIA_TYPE", mediaType)
                Log.d("MEDIA_URL", mediaUrl)
                if (mediaType == "image") {
                    requestImage(mediaUrl)
                    // ivPicture.setOnClickListener { requestImage(mediaUrlHD) } // TODO: future feature to open in pop-up
                } else if (mediaType == "video") {
                    progressBar.visibility = View.GONE
                    vvVideo.visibility = View.VISIBLE
                    val mediaController = MediaController(this)
                    vvVideo.setVideoURI(Uri.parse(mediaUrl))
                    vvVideo.setMediaController(mediaController)
                    vvVideo.requestFocus()
                    vvVideo.start()
                    // TODO: error to play video
                    // https://stackoverflow.com/questions/13814055/how-to-play-youtube-videos-in-android-video-view
                } else {
                    Toast.makeText(this, "UNKNOWN MEDIA TYPE", Toast.LENGTH_SHORT).show()
                }
                showUI(true)
            },
            { Toast.makeText(this, getString(R.string.error_load_data), Toast.LENGTH_SHORT).show() }))
    }

    private fun requestImage (url: String) {
        ivPicture.setImageBitmap(null)
        requestQueue.add(ImageRequest(url,
            {
                Log.d("URL_IMAGE_VIEW", url)
                progressBar.visibility = View.GONE
                ivPicture.visibility = View.VISIBLE
                ivPicture.setImageBitmap(it)
            }, 0, 0, ScaleType.FIT_XY, Bitmap.Config.ALPHA_8,
            {
                Toast.makeText(this, getString(R.string.error_load_media), Toast.LENGTH_SHORT).show()
                ivPicture.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.outline_broken_image_24))
            }))
    }

    private fun showUI (canSee: Boolean) {
        if (canSee) {
            ivInfo.visibility = View.VISIBLE
            bDate.visibility = View.VISIBLE
            tvTitle.visibility = View.VISIBLE
            tvExplanation.visibility = View.VISIBLE
            tvCopyright.visibility = View.VISIBLE
            bOpenInBrowser.visibility = View.VISIBLE
            tvVersion.visibility = View.VISIBLE
        }  else {
            progressBar.visibility = View.VISIBLE
            ivInfo.visibility = View.GONE
            bDate.visibility = View.GONE
            calendarView.visibility = View.GONE
            tvTitle.visibility = View.GONE
            tvExplanation.visibility = View.GONE
            tvCopyright.visibility = View.GONE
            ivPicture.visibility = View.GONE
            vvVideo.visibility = View.GONE
            bOpenInBrowser.visibility = View.GONE
            tvVersion.visibility = View.GONE
        }
    }

    private fun saveApiKey () {
        Log.d("API_KEY", apiKey)
        Toast.makeText(this, getString(R.string.api_key_saved), Toast.LENGTH_SHORT).show()
        sharedPreferences.edit {
            putString("API_KEY", apiKey)
            commit()
        }
    }

    private fun requestAPIKey () {
        val etApiKey = EditText(baseContext)
        etApiKey.setTextColor(getColor(R.color.nasa_red))
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.api_key))
            .setMessage(getString(R.string.api_key_insert))
            .setView(etApiKey)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                apiKey = etApiKey.text.toString()
                saveApiKey()
                dialog.dismiss()
                loadUI()
            }
            .show()
    }

    private fun loadUI () {
        val today = Calendar.getInstance()
        val todayDay = today.get(Calendar.DATE)
        val todayMonth = today.get(Calendar.MONTH) + 1
        val todayYear = today.get(Calendar.YEAR)
        requestJson(todayYear, todayMonth, todayDay)
    }
}