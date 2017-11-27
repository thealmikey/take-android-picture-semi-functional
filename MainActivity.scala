package com.almikey.functionalpic

import scala.collection.JavaConversions._
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

import android.net.Uri
import android.os.Bundle
import android.content.pm.{PackageManager, ResolveInfo}
import android.content.{ComponentName, Intent}
import android.graphics.{Bitmap, BitmapFactory}
import android.widget.{Button, ImageView}
import android.provider.MediaStore
import android.support.v4.content.FileProvider
import android.view.View
import android.support.v7.app.AppCompatActivity
import android.util.Log
import cats.effect.IO
import Helpers._



//a helper to make the OnClick callbacks less verbose
object Helpers {

  implicit def onClick(handler: View => Unit): View.OnClickListener =
    new View.OnClickListener {
      override def onClick(view: View): Unit = handler(view)
    }
}

class MainActivity extends AppCompatActivity{

  var mImageView: ImageView = _
  var mCurrentPhotoPath: String = _


  var REQUEST_TAKE_PHOTO: Int = 1

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)
    var theButton: Button = findViewById(R.id.theButton).asInstanceOf[Button]
    var takePicButton: Button = findViewById(R.id.takePicButton)
      .asInstanceOf[Button]
    mImageView = findViewById(R.id.theImageView).asInstanceOf[ImageView]

    takePicButton.setOnClickListener((view: View) =>
      dispatchTakePictureIntent().unsafeRunSync()
    )
  }

  override def onActivityResult(requestCode: Int,
                                resultCode: Int,
                                data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == REQUEST_TAKE_PHOTO) {
      setPic()
    }
  }
  private def dispatchTakePictureIntent() =
    for {
      file <- createImageFile
      intent <- makePictureIntent
      _ <- takePicture(file, intent)
    } yield ()

  private def createImageFile():IO[File] = IO{
    // Create an image file name
    val timeStamp: String =
      new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())
    val imageFileName: String = "JPEG_" + timeStamp + "_"
    val storageDir: File = getExternalFilesDir("images")
    val image: File = File.createTempFile(imageFileName, ".jpg", storageDir)
    /* directory */

    // Save a file: path for use with ACTION_VIEW intents
    mCurrentPhotoPath = image.getAbsolutePath
    image
  }


  private def makePictureIntent: IO[Option[Intent]] = IO {
    // Ensure that there's a camera activity to handle the intent
    val takePictureIntent: Intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    val pictureTaker: Option[ComponentName] =
      Option(takePictureIntent.resolveActivity(getPackageManager))
    pictureTaker match {
      case Some(_) => Some(takePictureIntent)
      case None    => None
    }
  }

  private def takePicture(photoFile: File, intent: Option[Intent]): IO[Unit] =
    IO {
      intent match {
        case Some(takePictureIntent) =>
          // Create the File where the photo should go
          Log.i("i found", photoFile.getAbsolutePath)
          val photoURI: Uri =
            FileProvider.getUriForFile(this,
              "com.almikey.simpletodo.fileprovider",
              photoFile)
          takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
          var resolvedIntentActivities: List[ResolveInfo] =
            getApplicationContext.getPackageManager
              .queryIntentActivities(takePictureIntent,
                PackageManager.MATCH_DEFAULT_ONLY)
              .toList
          for (resolvedIntentInfo <- resolvedIntentActivities) {
            val packageName = resolvedIntentInfo.activityInfo.packageName
            getApplicationContext.grantUriPermission(
              packageName,
              photoURI,
              Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
          startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO)

        case None => Log.i("no activity", "no component to handle photo taking")

      }
    }

  private def setPic(): Unit = {
    // Get the dimensions of the View
    val targetW: Int = mImageView.getWidth
    val targetH: Int = mImageView.getHeight
    // Get the dimensions of the bitmap
    val bmOptions: BitmapFactory.Options = new BitmapFactory.Options()
    bmOptions.inJustDecodeBounds = true
    BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions)
    val photoW: Int = bmOptions.outWidth
    val photoH: Int = bmOptions.outHeight
    // Determine how much to scale down the image
    val scaleFactor: Int = Math.min(photoW / targetW, photoH / targetH)
    // Decode the image file into a Bitmap sized to fill the View
    bmOptions.inJustDecodeBounds = false
    bmOptions.inSampleSize = scaleFactor
    bmOptions.inPurgeable = true
    Log.i("i got here in setpic", mCurrentPhotoPath)
    val bitmap: Bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions)
    mImageView.setImageBitmap(bitmap)
  }



}

