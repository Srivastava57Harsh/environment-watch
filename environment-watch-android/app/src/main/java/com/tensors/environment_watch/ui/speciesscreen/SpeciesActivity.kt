package com.tensors.environment_watch.ui.speciesscreen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.tensors.environment_watch.R
import com.tensors.environment_watch.api.GalleryAdapter
import com.tensors.environment_watch.api.Species
import kotlinx.android.synthetic.main.activity_gallery.*
import kotlinx.android.synthetic.main.activity_species.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.time.LocalDateTime
import java.util.*

class SpeciesActivity : AppCompatActivity() {
    lateinit var species: Species
    lateinit var tfLite: Interpreter
    lateinit var accuracy: String
    var dayView = false

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = assets.openFd("bird_classification.tflite")
        return FileInputStream(fileDescriptor.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_species)

        try {
            tfLite = Interpreter(loadModelFile())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        val database = Firebase.database.reference

        mapFragment.getMapAsync { googleMap ->
            everCoords(googleMap, database)
        }

        day_coords.setOnClickListener {
            dayView = if(dayView) {
                Toast.makeText(this, "Switching view to show all images ever.", Toast.LENGTH_SHORT).show()
                mapFragment.getMapAsync { googleMap ->
                    everCoords(googleMap, database)
                }
                false
            } else {

                Toast.makeText(this, "Switching view to just show the last 24 hours of images.", Toast.LENGTH_SHORT).show()
                mapFragment.getMapAsync { googleMap ->
                    dayCoords(googleMap, database)
                }
                true
            }
        }

        setUpView()
    }

    private fun dayCoords(googleMap: GoogleMap, database: DatabaseReference) {
        googleMap.clear()
        FirebaseStorage.getInstance().reference.child("coords/${species.httpRequestName}")
            .listAll().addOnSuccessListener { listResult ->
                listResult?.items?.forEach { storageReference ->
                    storageReference.getBytes(1024 * 1024).addOnSuccessListener { byteArray ->
                        val (lat, lon, time) = String(byteArray).split(" ")
                        val currentTime = Date().time
                        database.child("imageData").child(storageReference.name).addListenerForSingleValueEvent(object:
                            ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                if(snapshot.value != null && (snapshot.child("likes").value as Long - snapshot.child("dislikes").value as Long) > -2 && (currentTime-86400000..currentTime+86400000).contains(time.toLong())) {
                                    googleMap.addMarker(
                                        MarkerOptions().position(
                                            LatLng(
                                                lat.toDouble(),
                                                lon.toDouble()
                                            )
                                        )
                                    )
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {}

                        })

                    }
                }
            }
    }

    private fun everCoords(googleMap: GoogleMap, database: DatabaseReference) {
        Log.e("Smail", "1")
        googleMap.clear()
        FirebaseStorage.getInstance().reference.child("coords/${species.httpRequestName}")
            .listAll().addOnSuccessListener { listResult ->
                listResult?.items?.forEach { storageReference ->
                    storageReference.getBytes(1024 * 1024).addOnSuccessListener { byteArray ->
                        val (lat, lon, time) = String(byteArray).split(" ")
                        database.child("imageData").child(storageReference.name).addListenerForSingleValueEvent(object:
                            ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                if(snapshot.value != null && (snapshot.child("likes").value as Long - snapshot.child("dislikes").value as Long)  > -2) {
                                    googleMap?.addMarker(
                                        MarkerOptions().position(
                                            LatLng(
                                                lat.toDouble(),
                                                lon.toDouble()
                                            )
                                        )
                                    )
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {}

                        })

                    }
                }
            }
    }

    private fun setUpView() {
        species = Species(
            intent.getStringExtra("name")!!,
            intent.getStringExtra("fullName")!!,
            intent.getStringExtra("httpRequestName")!!,
            intent.getStringExtra("state")!!,
            intent.getStringExtra("fullDescription")!!
        )

        specific_species_name.text = species.name
        specific_species_full_name.text = species.fullName
        specific_species_state.text = species.state
        specific_species_description.text = species.description

        when (species.name) {
            "Red-cockaded Woodpecker" -> specific_species_image.setImageResource(R.drawable.specific1)
            "Yellow-billed Cuckoo" -> specific_species_image.setImageResource(R.drawable.specific2)
            "Florida Jay" -> specific_species_image.setImageResource(R.drawable.specific3)
            "Rusty Blackbird" -> specific_species_image.setImageResource(R.drawable.specific4)
            "Red-legged Kittiwake" -> specific_species_image.setImageResource(R.drawable.specific5)
        }

        species_fab.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ), 0
                )
            } else {
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(cameraIntent, 0)
            }
        }

        specific_species_gallery.setOnClickListener {
            val intent = Intent(this, GalleryActivity::class.java)


            intent.putExtra("requestName", species.httpRequestName)
            startActivity(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED
            && grantResults[1] == PackageManager.PERMISSION_GRANTED
            && grantResults[2] == PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(cameraIntent, 0)
        } else {
            Toast.makeText(this, "Please grant all the permissions.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val image = data?.extras?.get("data")

        if (image != null && interpretImageMatches(image as Bitmap)) {
            uploadImage(image)
        } else if(resultCode != RESULT_CANCELED)  {
            Toast.makeText(this, "Your image was too blurry or not an image of the ${species.name}. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun interpretImageMatches(image: Bitmap): Boolean {
        val imageTensorIndex = 0
        val imageShape = tfLite.getInputTensor(imageTensorIndex).shape()

        val imageSizeY = imageShape[1]
        val imageSizeX = imageShape[2]
        val imageDataType = tfLite.getInputTensor(imageTensorIndex).dataType()

        val probabilityTensorIndex = 0
        val probabilityShape = tfLite.getOutputTensor(probabilityTensorIndex).shape()

        val probabilityDataType = tfLite.getOutputTensor(probabilityTensorIndex).dataType()

        var inputImageBuffer = TensorImage(imageDataType)
        val outputProbabilityBuffer =
            TensorBuffer.createFixedSize(probabilityShape, probabilityDataType)
        val probabilityProcessor = TensorProcessor.Builder().add(NormalizeOp(0.0f, 255.0f)).build()

        val cropSize = image.width.coerceAtMost(image.height)
        inputImageBuffer.load(image)
        inputImageBuffer = ImageProcessor.Builder().add(ResizeWithCropOrPadOp(cropSize, cropSize))
            .add(ResizeOp(imageSizeX, imageSizeY, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR)).build()
            .process(inputImageBuffer)
        tfLite.run(inputImageBuffer.buffer, outputProbabilityBuffer.buffer.rewind())

        var labels = mutableListOf("")
        try {
            labels = FileUtil.loadLabels(this, "labels.txt")
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        val labeledProbability =
            TensorLabel(labels, probabilityProcessor.process(outputProbabilityBuffer))
                .mapWithFloatValue
        val maxValueInMap: Float = Collections.max(labeledProbability.values)

        for (entry in labeledProbability.entries) {
            Log.e("Smail", "Max: ${entry.key}, ${entry.value}")
            if (entry.value == maxValueInMap) {
                accuracy = entry.value.toString()
            }

            if (entry.value >= 0.03) {
                return true
            }
        }

        return false
    }

    private fun uploadImage(image: Bitmap) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            finish()
            Toast.makeText(
                this,
                "Please accept location permissions in settings.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        if(connectivityManager.activeNetworkInfo != null && connectivityManager.activeNetworkInfo.isConnected) {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers: List<String> = locationManager.getProviders(true)
            var bestLocation: Location? = null
            for (provider in providers) {
                val location = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || location.accuracy < bestLocation.accuracy) {
                    bestLocation = location
                }
            }

            val lat = bestLocation?.latitude
            val lon = bestLocation?.longitude

            val name = UUID.randomUUID().toString()
            if (lat != null && lon != null) {
                val tempFile = File.createTempFile(name, null)
                tempFile.writeText("$lat $lon ${Date().time}")
                FirebaseStorage.getInstance().reference.child("coords/${species.httpRequestName}/${name}")
                    .putFile(tempFile.toUri())
                    .addOnSuccessListener { p0 ->
                        Toast.makeText(applicationContext, "File Uploaded", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { p0 ->
                        Toast.makeText(applicationContext, p0.message, Toast.LENGTH_SHORT).show()
                    }
            }

            val tempUri = getImageUri(applicationContext, image)
            FirebaseStorage.getInstance().reference.child("images/${species.httpRequestName}/$name")
                .putFile(tempUri!!)
                .addOnSuccessListener { p0 ->
                    Toast.makeText(applicationContext, "File Uploaded", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { p0 ->
                    Toast.makeText(applicationContext, p0.message, Toast.LENGTH_SHORT).show()
                }

            val database = Firebase.database.reference
            database.child("imageData").updateChildren(mutableMapOf(Pair(name, ImageData(name, 0, 0))) as Map<String, Any>)

        }

        startActivity(Intent(this, ResultsActivity::class.java))

    }

    private fun getImageUri(inContext: Context, inImage: Bitmap): Uri? {
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, ByteArrayOutputStream())
        return Uri.parse(
            MediaStore.Images.Media.insertImage(
                inContext.contentResolver,
                inImage,
                UUID.randomUUID().toString(),
                null
            )
        )
    }

}

class ImageData(
    val name: String,
    var likes: Long,
    var dislikes: Long
)
