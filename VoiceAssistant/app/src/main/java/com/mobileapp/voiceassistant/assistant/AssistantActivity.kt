package com.mobileapp.voiceassistant.assistant

import android.Manifest
import android.animation.Animator
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.hardware.camera2.CameraManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.mobileapp.voiceassistant.R
import com.mobileapp.voiceassistant.data.AssistantDatabase
import com.mobileapp.voiceassistant.databinding.ActivityAssistantBinding
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import android.provider.AlarmClock




class AssistantActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAssistantBinding
    private lateinit var assistantViewModel: AssistantViewModel
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var keeper: String

    private var REQUESTCALL = 1
    private var SENDSMS = 2
    private var READSMS = 3
    private var SHAREFILE = 4
    private var SHAREATEXTFILE = 5
    private var READCONTACTS = 6
    private var CAPTUREPHOTO = 7

    private var REQUEST_CODE_SELECT_DOC: Int = 100
    private var REQUEST_ENABLE_BT=1000

    private var bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private lateinit var cameraManager: CameraManager
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var cameraID: String
    private lateinit var ringtone: Ringtone

    private var imageIndex: Int = 0
    private lateinit var  imgUri: Uri
//    private lateinit var helper: Open


    @Suppress("DEPRECATION")
    private val imageDirectory = Environment
        .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(R.anim.non_movable, R.anim.non_movable)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_assistant)

        val application = requireNotNull(this).application
        val dataSource = AssistantDatabase.getInstance(application).assistantDao
        val viewModelFactory = AssistantViewModelFactory(dataSource,application)

        assistantViewModel = ViewModelProvider(this,viewModelFactory)
            .get(AssistantViewModel::class.java)

        val adapter = AssistantAdapter()
        binding.recyclerview.adapter = adapter

        assistantViewModel.message.observe(this, {
            it?.let { adapter.data = it }
        })
        binding.setLifecycleOwner(this)

        if(savedInstanceState==null){
            binding.assistantConstraintLayout.setVisibility(View.INVISIBLE)

            val viewTreeObserver: ViewTreeObserver = binding.assistantConstraintLayout
                .viewTreeObserver
            if(viewTreeObserver.isAlive){
                viewTreeObserver.addOnGlobalLayoutListener (object: ViewTreeObserver.OnGlobalLayoutListener{
                    override fun onGlobalLayout() {
                        circularRevealActivity()
                        binding.assistantConstraintLayout.viewTreeObserver.removeOnGlobalLayoutListener { this }
                    }})

            }
        }

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try{
            cameraID=cameraManager.cameraIdList[0]
        }catch (e: java.lang.Exception){
            e.printStackTrace()
        }


        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        ringtone = RingtoneManager.getRingtone(applicationContext,RingtoneManager.
        getDefaultUri(RingtoneManager.TYPE_RINGTONE))

        textToSpeech = TextToSpeech(this){
            status ->
            if(status==TextToSpeech.SUCCESS){
                val result: Int = textToSpeech.setLanguage(Locale.ENGLISH)

                if(result==TextToSpeech.LANG_MISSING_DATA ||
                        result==TextToSpeech.LANG_NOT_SUPPORTED){
                    Log.e("tts","tts")
                }
            }
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        speechRecognizer.setRecognitionListener(object: RecognitionListener {
            override fun onReadyForSpeech(p0: Bundle?) {
                Log.d("imp", "onReadyForSpeech")
            }

            override fun onBeginningOfSpeech() {
                Log.d("imp", "onBeginningOfSpeech")
            }

            override fun onRmsChanged(p0: Float) {
                Log.d("imp", "onRmsChanged")
            }

            override fun onBufferReceived(p0: ByteArray?) {
                Log.d("imp", "onBufferReceived")
            }

            override fun onEndOfSpeech() {
                Log.d("imp", "onEndOfSpeech")
            }

            override fun onError(p0: Int) {
                Log.d("imp", "onError" + p0)
            }

            override fun onResults(bundle: Bundle?) {

                val data = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(data!=null){
                    keeper = data[0]
                    Log.d("MSG: ", keeper)
                    when{
                        keeper.contains("thanks") -> speak("It's my job, let me know if there is something else");
                        keeper.contains("welcome") -> speak("For what?")
                        keeper.contains("clear") -> assistantViewModel.onClear()
                        keeper.contains("date") -> getDate()
                        keeper.contains("time") -> getTime()
                        keeper.contains("phone call") -> makeAPhoneCall()
                        keeper.contains("send SMS") -> sendSMS()
                        keeper.contains("read my last SMS") -> readSMS()
                        keeper.contains("open Gmail") -> openGmail()
                        keeper.contains("open WhatsApp") -> openWhatsapp()
                        keeper.contains("open map") -> openMap()
                        keeper.contains("weather") -> openWeather()
                        keeper.contains("open Facebook") -> openFacebook()
                        keeper.contains("open SMS") -> openMessages()
                        keeper.contains("turn on Bluetooth") -> turnOnBluetooth()
                        keeper.contains("turn off Bluetooth") -> turnOfBluetooth()
                        keeper.contains("turn on flash") -> turnOnFlash()
                        keeper.contains("turn off flash") -> turnOffFlash()
                        keeper.contains("open camera") -> openCamera()
                        keeper.contains("play ringtone") -> playRingtone()
                        keeper.contains("stop ringtone") -> stopRingtone()
                        keeper.contains("set alarm") -> setAlarm()
                        keeper.contains("tell me a joke") -> joke()
                        keeper.contains("tell me a fact") -> fact()
                        keeper.contains("hello") -> speak("Hello, How can I help you")
                        else -> speak("I'm not understanding. Please say again")

                    }
                }
            }

            override fun onPartialResults(p0: Bundle?) {
                TODO("Not yet implemented")
            }

            override fun onEvent(p0: Int, p1: Bundle?) {
                TODO("Not yet implemented")
            }

        })

        binding.assistantActionButton.setOnTouchListener { view, motionEvent ->
            when (motionEvent.action){
                MotionEvent.ACTION_UP -> {
                    speechRecognizer.stopListening()
                }
                MotionEvent.ACTION_DOWN -> {
                    textToSpeech.stop()
                    speechRecognizer.startListening(recognizerIntent)
                }
            }
            false
        }

        checkIfSpeechRecognizerAvailable()


    }

    private fun joke(){
        speak("You don't need a parachute to go skydiving. You need a parachute to go skydiving twice")
    }

    private fun fact(){
        speak("The world’s oldest wooden wheel has been around for more than 5,000 years")
    }

    private fun checkIfSpeechRecognizerAvailable() {
        if(SpeechRecognizer.isRecognitionAvailable(this)){
            Log.d("log", "yes")
        }else {
            Log.d("log", "No")
        }
    }

    fun speak(text: String){
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
        assistantViewModel.sendMessageToDatabase(keeper, text)
    }

    fun getDate(){
        val calendar = Calendar.getInstance()
        val formattedDate = DateFormat.getDateInstance(DateFormat.FULL).format(calendar.time)
        val splitDate = formattedDate.split(",").toTypedArray()
        val date = splitDate[1].trim{ it <= ' '}
        speak("The date is $date")
    }

    fun getTime(){
        val calendar = Calendar.getInstance()
        val format = SimpleDateFormat("HH:mm:ss")
        val time:String = format.format(calendar.getTime())
        speak("The time is $time")
    }

    private fun setAlarm(){
        val mClockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        mClockIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(mClockIntent)
    }


    private fun makeAPhoneCall(){
        val keeperSplit=keeper.replace(" ".toRegex(), "").split("o").toTypedArray()
        val number = keeperSplit[1]

        if(number.trim {  it <= ' ' }.length>0){
            if(ContextCompat.checkSelfPermission(this,
                Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED
            )
            {
                ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CALL_PHONE), REQUESTCALL)

            }else{
                val dial = "tel:$number"
                speak("Calling $number")
                startActivity(Intent(Intent.ACTION_CALL, Uri.parse(dial)))
            }

        }else{
            Toast.makeText(this," Enter Phone No", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendSMS(){
        if(ContextCompat.checkSelfPermission(this,
                Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED
        ){
            ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.SEND_SMS), SENDSMS)
        }else{
            val keeperReplaced = keeper.replace(" ".toRegex(), "")
            val number = keeperReplaced.split("tocontactnumber").toTypedArray()[1]
            val message = keeper.split("that").toTypedArray()[1]

            val mySmsManager = SmsManager.getDefault()
            mySmsManager.sendTextMessage(
                number.trim{ it <= ' '},
                null,
                message.trim{ it <= ' '},
                null,
                null
            )
            speak("Message sent that $message")
        }
    }

    private fun readSMS(){
        if(ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED
        ){
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.READ_SMS), READSMS)
        }else{
            val cursor = contentResolver.query(Uri.parse("content://sms"),
            null,null,null);
            cursor!!.moveToFirst()
            speak("Your last message was " + cursor.getString(12))
        }
    }

    private fun openMessages(){
        val intent =
            packageManager.getLaunchIntentForPackage(Telephony.Sms.getDefaultSmsPackage(this))
        intent?.let { startActivity(it) }

    }

    private fun openMap(){
        val intent =
            packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
        intent?.let { startActivity(it) }
    }

    private fun openWeather(){
        speak("Current weather is 18 degree celcius with wind gust of 19km per hour and air quality is poor")
    }

    private fun openFacebook(){
        val intent =
            packageManager.getLaunchIntentForPackage("com.facebook.katana")
        intent?.let { startActivity(it) }
    }

    private fun openWhatsapp(){
        val intent =
            packageManager.getLaunchIntentForPackage("com.whatsapp")
        intent?.let { startActivity(it) }
    }

    private fun openGmail(){
        val intent =
            packageManager.getLaunchIntentForPackage("com.google.android.gm")
        intent?.let { startActivity(it) }
    }

    private fun turnOnBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            speak("Turning on bluetooth")
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent, REQUEST_ENABLE_BT)
        }else{
            speak("Bluetooth is already on")
        }
    }

    private fun turnOfBluetooth() {
        if (bluetoothAdapter.isEnabled) {
            bluetoothAdapter.disable()
            speak("Turning bluetooth off")
        }else{
            speak("Bluetooth is already off")
        }
    }

    private fun turnOnFlash(){
        try{
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
                cameraManager.setTorchMode(cameraID, true)
                speak("Flash turned on")
            }
        }catch (e: java.lang.Exception){

        }
    }

    private fun turnOffFlash(){
        try{
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
                cameraManager.setTorchMode(cameraID, false)
                speak("Flash turned off")
            }
        }catch (e: java.lang.Exception){

        }
    }

    private fun capturePhoto(){
        if(ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED
        ){
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE), CAPTUREPHOTO)
        }else{
            val builder = StrictMode.VmPolicy.Builder()
            StrictMode.setVmPolicy(builder.build())
            imageIndex++
            val file: String = imageDirectory+imageIndex+".jpg"
            val newFile = File(file)
                try{
                    newFile.createNewFile()
                }catch (e: IOException){

                }

            val outputFileUri = Uri.fromFile(newFile)
            val cameraIntent = Intent(MediaStore.EXTRA_OUTPUT, outputFileUri)
            startActivity(cameraIntent)
            speak("Photo will be saved to $file")


        }
    }

    private fun openCamera(){

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivity(cameraIntent)
    }


    private fun playRingtone(){
        speak("Ringtone playing")
        ringtone.play()
    }

    private fun stopRingtone(){
        speak("Ringtone Stopped")
        ringtone.stop()
    }

    private fun circularRevealActivity(){
        val cx:Int = binding.assistantConstraintLayout.getRight() - getDips(44)
        val cy:Int = binding.assistantConstraintLayout.getBottom() - getDips(44)

        val finalRadius: Int = Math.max(
            binding.assistantConstraintLayout.getWidth(),
            binding.assistantConstraintLayout.getHeight(),

            )

        val circularReveal = ViewAnimationUtils.createCircularReveal(
            binding.assistantConstraintLayout,
            cx,
            cy,
            0f,
            finalRadius.toFloat()
        )

        circularReveal.duration = 1250
        binding.assistantConstraintLayout.setVisibility(View.VISIBLE)
        circularReveal.start()


    }

    private fun getDips(dps: Int): Int{
        val resources : Resources = resources
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dps.toFloat(),
            resources.getDisplayMetrics()
        ).toInt()
    }

    override fun onBackPressed() {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP) {
            val cx: Int = binding.assistantConstraintLayout.getWidth() - getDips(44)
            val cy: Int = binding.assistantConstraintLayout.getHeight() - getDips(44)

            val finalRadius: Int = Math.max(
                binding.assistantConstraintLayout.getWidth(),
                binding.assistantConstraintLayout.getHeight()
            )

            val circularReveal =
                ViewAnimationUtils.createCircularReveal(
                    binding.assistantConstraintLayout, cx, cy,
                    finalRadius.toFloat(), 0f
                )

            circularReveal.addListener(object : Animator.AnimatorListener {

                override fun onAnimationStart(p0: Animator?) {
                    TODO("Not yet implemented")
                }

                override fun onAnimationEnd(animation: Animator?, isReverse: Boolean) {
                    binding.assistantConstraintLayout.setVisibility(View.INVISIBLE)
                    finish()
                }

                override fun onAnimationEnd(p0: Animator?) {
                    TODO("Not yet implemented")
                }

                override fun onAnimationCancel(p0: Animator?) {
                    TODO("Not yet implemented")
                }

                override fun onAnimationRepeat(p0: Animator?) {
                    TODO("Not yet implemented")
                }
            })

            circularReveal.duration = 1250
            circularReveal.start()
        }else{
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
        speechRecognizer.cancel()
        speechRecognizer.destroy()
    }
}