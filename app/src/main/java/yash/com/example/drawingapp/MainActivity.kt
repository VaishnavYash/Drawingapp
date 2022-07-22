package yash.com.example.drawingapp

import android.Manifest
import android.app.Dialog
import android.app.appsearch.AppSearchSchema
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.Image
import android.media.MediaScannerConnection
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var drawingView: DrawingView? = null
    private var mImageButtonCurrentPaint: ImageButton? = null

    var customProgressDialog : Dialog ?= null

    private var galleryLauncher: ActivityResultLauncher <Intent> =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()){
            result ->
            if(result.data!=null && result.resultCode == RESULT_OK){
                val imageBackground : ImageView = findViewById(R.id.backgrndImageMain)
                imageBackground.setImageURI(result.data?.data)
            }
        }

    private val mediaResultLauncher: ActivityResultLauncher <Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()){
            permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value

                if(isGranted){
                    Toast.makeText(
                        this@MainActivity,
                        "Permission Granted", Toast.LENGTH_LONG
                    ).show()

                    val imagePick = Intent(Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    galleryLauncher.launch(imagePick)


                }else{
                    if(permissionName == Manifest.permission.READ_EXTERNAL_STORAGE){
                        Toast.makeText(this,
                            "Permission NOT Granted", Toast.LENGTH_LONG).show()
                    }
                }

            }

        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setBrushSize(20.toFloat())

        var linearLayoutPaintBtn = findViewById<LinearLayout>(R.id.colorsBtns)


        mImageButtonCurrentPaint = linearLayoutPaintBtn[0] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
        )

        val brushBtn: ImageButton = findViewById(R.id.brushBtn)
        brushBtn.setOnClickListener {
            brushSizeDialog()
        }

        val galleryBtn: ImageButton = findViewById(R.id.gallery_btn)
        galleryBtn.setOnClickListener {
            requestStoragePermission()
        }

        val undoBtn: ImageButton = findViewById(R.id.undo_btn)
        undoBtn.setOnClickListener {
            drawingView?.onUndoClick()
        }

        val redoBtn: ImageButton = findViewById(R.id.redo_btn)
        redoBtn.setOnClickListener {
            drawingView?.onRedoClick()
        }

        val colorPickerBtn: ImageButton = findViewById(R.id.colorPicker)
        colorPickerBtn.setOnClickListener{
            colorPickerDialog()
        }

        val saveBtn: ImageButton = findViewById(R.id.btnSave)
        saveBtn.setOnClickListener {
            if(isReadStorageAllowed()){
                showProgressDialog()
                lifecycleScope.launch {
                    val flDrawingView:FrameLayout = findViewById(R.id.ft_drawing_view_container)
                    saveBitmapFile(getBitmapFromView(flDrawingView))

                }
            }
        }

    }

    private fun brushSizeDialog() {

        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush)
        brushDialog.setTitle("Brush Size: ")

        var smallBtn: ImageButton = brushDialog.findViewById(R.id.small_brush)
        var mediumBtn: ImageButton = brushDialog.findViewById(R.id.medium_brush)
        var largeBtn: ImageButton = brushDialog.findViewById(R.id.large_brush)

        smallBtn.setOnClickListener {
            drawingView?.setBrushSize(10.toFloat())
            brushDialog.dismiss()
        }
        mediumBtn.setOnClickListener {
            drawingView?.setBrushSize(20.toFloat())
            brushDialog.dismiss()
        }
        largeBtn.setOnClickListener {
            drawingView?.setBrushSize(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun paintClicked(view: View) {
        if (view !== mImageButtonCurrentPaint) {
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
            )
            mImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_normal)
            )
            mImageButtonCurrentPaint = view
        }
    }

    private fun colorPickerDialog() {
        val colorDialogs = Dialog(this)
        colorDialogs.setContentView(R.layout.color_picker)
        colorDialogs.setTitle("Color Picker: ")

        var colorA: SeekBar = colorDialogs.findViewById(R.id.colorA)
        var colorR: SeekBar = colorDialogs.findViewById(R.id.colorR)
        var colorG: SeekBar = colorDialogs.findViewById(R.id.colorG)
        var colorB: SeekBar = colorDialogs.findViewById(R.id.colorB)

        var strColor : EditText =colorDialogs.findViewById(R.id.strColor)
        var btnColorPreview : Button =colorDialogs.findViewById(R.id.btnColorPreview)
        var colorOkBtn : Button =colorDialogs.findViewById(R.id.colorOkBtn)

        strColor.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s: Editable) {
                if (s.length == 6) {
                    colorA.progress = 255
                    colorR.progress = Integer.parseInt(s.substring(0..1), 16)
                    colorG.progress = Integer.parseInt(s.substring(2..3), 16)
                    colorB.progress = Integer.parseInt(s.substring(4..5), 16)
                } else if (s.length == 8) {
                    colorA.progress = Integer.parseInt(s.substring(0..1), 16)
                    colorR.progress = Integer.parseInt(s.substring(2..3), 16)
                    colorG.progress = Integer.parseInt(s.substring(4..5), 16)
                    colorB.progress = Integer.parseInt(s.substring(6..7), 16)
                }
            }

            override fun beforeTextChanged(
                s: CharSequence, start: Int,
                count: Int, after: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence, start: Int,
                before: Int, count: Int
            ) {

            }
        })

        fun getColorString(): String {
            var a = Integer.toHexString(((255*colorA.progress)/colorA.max))
            if(a.length==1) a = "0$a"
            var r = Integer.toHexString(((255*colorR.progress)/colorR.max))
            if(r.length==1) r = "0$r"
            var g = Integer.toHexString(((255*colorG.progress)/colorG.max))
            if(g.length==1) g = "0$g"
            var b = Integer.toHexString(((255*colorB.progress)/colorB.max))
            if(b.length==1) b = "0$b"
            return "#$a$r$g$b"
        }

        colorA.max = 255
        colorA.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(
                seekBar: SeekBar, progress: Int,
                fromUser: Boolean
            ) {
                val colorStr = getColorString()
                strColor.setText(colorStr.replace("#", "").toUpperCase())
//                btnColorPreview.setBackgroundColor(Color.parseColor(colorStr))
            }
        })

        colorR.max = 255
        colorR.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(
                seekBar: SeekBar, progress: Int,
                fromUser: Boolean
            ) {
                val colorStr = getColorString()
                strColor.setText(colorStr.replace("#", "").toUpperCase())
                btnColorPreview.setBackgroundColor(Color.parseColor(colorStr))
            }
        })

        colorG.max = 255
        colorG.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(
                seekBar: SeekBar, progress: Int,
                fromUser: Boolean
            ) {
                val colorStr = getColorString()
                strColor.setText(colorStr.replace("#", "").toUpperCase())
//                btnColorPreview.setBackgroundColor(Color.parseColor(colorStr))
            }
        })

        colorB.max = 255
        colorB.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(
                seekBar: SeekBar, progress: Int,
                fromUser: Boolean
            ) {
                val colorStr = getColorString()
                strColor.setText(colorStr.replace("#", "").toUpperCase())
//                btnColorPreview.setBackgroundColor(Color.parseColor(colorStr))
            }
        })

        colorOkBtn.setOnClickListener {
            val color: String = getColorString()
            drawingView?.setColor(color)

            mImageButtonCurrentPaint!!.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_normal)
            )
            colorDialogs.dismiss()
        }
        colorDialogs.show()
    }

    private fun isReadStorageAllowed(): Boolean{
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission(){
        if(ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
        ){
            showRationalDialog("Drawing App", "Drawing App needs access to Your Storage")
        }
        else{

            mediaResultLauncher.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private fun getBitmapFromView(view: View) : Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width,
            view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background

        if(bgDrawable!=null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas)

        return returnedBitmap
    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?) : String{
        var result = ""
        withContext(Dispatchers.IO){
            if(mBitmap != null){
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG,90, bytes)

                    val f = File(externalCacheDir?.absoluteFile.toString()+
                            File.separator + "Yash'sAPP" + System.currentTimeMillis() / 1000 + ".png")
                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()

                    result = f.absolutePath

                    runOnUiThread{
                        cancelProgressDialog()
                        if(result.isNotEmpty()){
                            Toast.makeText(
                                this@MainActivity,
                                "Image Saved Successfully : $result",Toast.LENGTH_SHORT
                            ).show()
                            shareFile(result)
                        }else{
                            Toast.makeText(                                this@MainActivity,
                                "Not able to save the Image",Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                }
                catch (e: Exception){
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun showRationalDialog(
        title: String,
        message: String,
    ) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel"){dialog, _->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun showProgressDialog(){
        customProgressDialog = Dialog(this@MainActivity)

        customProgressDialog?.setContentView(R.layout.custom_progress_dialog)

        customProgressDialog?.show()
    }

    private fun cancelProgressDialog(){
        if(customProgressDialog!=null){
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private fun shareFile(result: String){
        MediaScannerConnection.scanFile(this@MainActivity,
            arrayOf(result), null){
            path, uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM,uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }
    }

}