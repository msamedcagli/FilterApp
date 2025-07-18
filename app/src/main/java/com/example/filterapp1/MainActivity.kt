package com.example.filterapp1

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var selectImageFab: ImageView
    private lateinit var filterFab: ImageView
    private lateinit var faceFilterFab: ImageView
    private lateinit var saveFab: ImageView

    private var selectedImageUri: Uri? = null
    private var processedBitmap: Bitmap? = null
    private var currentFilterType: String = "original"

    companion object {
        private const val REQUEST_CODE_PICK_IMAGE = 100
        private const val PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, _ ->
            v.setPadding(0, 0, 0, 0)
            WindowInsetsCompat.CONSUMED
        }

        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV yüklenemedi!", Toast.LENGTH_SHORT).show()
        }

        imageView = findViewById(R.id.imageView)
        selectImageFab = findViewById(R.id.selectImageFab)
        filterFab = findViewById(R.id.filterFab)
        faceFilterFab = findViewById(R.id.faceFilterFab)
        saveFab = findViewById(R.id.saveFab)

        selectImageFab.setOnClickListener { openImageChooser() }
        filterFab.setOnClickListener { applyGrayscaleFilter() }
        faceFilterFab.setOnClickListener { applyGlassesFilter() }
        saveFab.setOnClickListener { saveImageToGallery() }

        checkPermissions()
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Tüm izinler verildi.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Bazı izinler reddedildi. Uygulama düzgün çalışmayabilir.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openImageChooser() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.data
            selectedImageUri?.let { uri ->
                val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                imageView.setImageBitmap(bitmap)
                processedBitmap = null // Reset processed bitmap when a new image is selected
            }
        }
    }

    private fun applyGrayscaleFilter() {
        selectedImageUri?.let { uri ->
            try {
                val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
                Utils.bitmapToMat(bitmap, mat)

                val grayMat = Mat()
                Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGBA2GRAY)

                val grayBitmap = Bitmap.createBitmap(grayMat.cols(), grayMat.rows(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(grayMat, grayBitmap)

                imageView.setImageBitmap(grayBitmap)
                processedBitmap = grayBitmap
                currentFilterType = "grayscale"
                Toast.makeText(this, "Resim siyah beyaza dönüştürüldü.", Toast.LENGTH_SHORT).show()

                mat.release()
                grayMat.release()
            } catch (e: Exception) {
                Toast.makeText(this, "Filtre uygulanırken hata oluştu: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        } ?: run {
            Toast.makeText(this, "Lütfen önce bir resim seçin.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageToGallery() {
        processedBitmap?.let { bitmap ->
            val filename = "${currentFilterType}_${System.currentTimeMillis()}.png"
            var fos: OutputStream? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { contentResolver.openOutputStream(it) }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val image = File(imagesDir, filename)
                fos = FileOutputStream(image)
            }

            fos?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                Toast.makeText(this, "Resim galeriye kaydedildi: $filename", Toast.LENGTH_LONG).show()
            } ?: run {
                Toast.makeText(this, "Resim kaydedilemedi.", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "Lütfen önce bir resim seçin ve filtre uygulayın.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyGlassesFilter() {
        selectedImageUri?.let { uri ->
            try {
                val glassesBitmap = BitmapFactory.decodeResource(resources, R.drawable.glasses)
                val inputStream = contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val srcMat = Mat()
                Utils.bitmapToMat(mutableBitmap, srcMat)
                Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_RGBA2BGRA)

                val grayMat = Mat()
                Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_BGRA2GRAY)

                val faceCascade = loadCascade(R.raw.haarcascade_frontalface_default)
                val eyeCascade = loadCascade(R.raw.haarcascade_eye)

                val faces = MatOfRect()
                faceCascade.detectMultiScale(grayMat, faces)

                for (faceRect in faces.toArray()) {
                    val faceROI = grayMat.submat(faceRect)
                    val eyes = MatOfRect()
                    eyeCascade.detectMultiScale(faceROI, eyes)

                    if (eyes.toArray().size >= 2) {
                        val eyeRects = eyes.toArray().sortedBy { it.x }
                        val eye1 = eyeRects[0]
                        val eye2 = eyeRects[1]

                        val eyeCenter1 = Point(faceRect.x + eye1.x + eye1.width / 2.0, faceRect.y + eye1.y + eye1.height / 2.0)
                        val eyeCenter2 = Point(faceRect.x + eye2.x + eye2.width / 2.0, faceRect.y + eye2.y + eye2.height / 2.0)

                        val angle = Math.toDegrees(Math.atan2(eyeCenter2.y - eyeCenter1.y, eyeCenter2.x - eyeCenter1.x))
                        val distance = Core.norm(MatOfPoint(eyeCenter1), MatOfPoint(eyeCenter2))

                        val glassesWidth = distance * 3.0
                        val glassesHeight = glassesWidth * glassesBitmap.height / glassesBitmap.width

                        val scaledGlasses = Bitmap.createScaledBitmap(glassesBitmap, glassesWidth.toInt(), glassesHeight.toInt(), true)
                        val glassesMat = Mat()
                        Utils.bitmapToMat(scaledGlasses, glassesMat)
                        Imgproc.cvtColor(glassesMat, glassesMat, Imgproc.COLOR_RGBA2BGRA)

                        val glassesCenter = Point((eyeCenter1.x + eyeCenter2.x) / 2, (eyeCenter1.y + eyeCenter2.y) / 2)
                        val rotationMatrix = Imgproc.getRotationMatrix2D(Point(glassesMat.cols() / 2.0, glassesMat.rows() / 2.0), angle, 1.0)

                        val rotatedGlasses = Mat()
                        Imgproc.warpAffine(glassesMat, rotatedGlasses, rotationMatrix, glassesMat.size(), Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(0.0,0.0,0.0,0.0))

                        val x = (glassesCenter.x - rotatedGlasses.cols() / 2).toInt()
                        val y = (glassesCenter.y - rotatedGlasses.rows() / 2).toInt()

                        val roiRect = Rect(x, y, rotatedGlasses.cols(), rotatedGlasses.rows())
                        if (roiRect.x >= 0 && roiRect.y >= 0 && roiRect.x + roiRect.width <= srcMat.cols() && roiRect.y + roiRect.height <= srcMat.rows()) {
                            val roi = srcMat.submat(roiRect)

                            val channels = ArrayList<Mat>()
                            Core.split(rotatedGlasses, channels)

                            if (channels.size == 4) {
                                val mask = channels[3]
                                rotatedGlasses.copyTo(roi, mask)
                            }
                        }
                    }
                }

                Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_BGRA2RGBA)
                val resultBitmap = Bitmap.createBitmap(srcMat.cols(), srcMat.rows(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(srcMat, resultBitmap)

                runOnUiThread {
                    imageView.setImageBitmap(resultBitmap)
                    processedBitmap = resultBitmap
                    currentFilterType = "glasses"
                    imageView.invalidate()
                    Toast.makeText(this, "Gözlük filtresi uygulandı.", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this, "Filtre uygulanırken hata oluştu: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } ?: run {
            Toast.makeText(this, "Lütfen önce bir resim seçin.", Toast.LENGTH_SHORT).show()
        }
    }




    private fun loadCascade(resourceId: Int): CascadeClassifier {
        val cascadeDir = getDir("cascade", Context.MODE_PRIVATE)
        val cascadeFile = File(cascadeDir, "cascade.xml")
        val inputStream = resources.openRawResource(resourceId)
        val outputStream = FileOutputStream(cascadeFile)
        val buffer = ByteArray(4096)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }
        inputStream.close()
        outputStream.close()
        val cascade = CascadeClassifier(cascadeFile.absolutePath)
        if (cascade.empty()) {
            throw RuntimeException("Failed to load cascade classifier")
        }
        cascadeFile.delete()
        return cascade
    }
}
