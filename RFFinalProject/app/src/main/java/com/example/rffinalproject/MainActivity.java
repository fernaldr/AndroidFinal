package com.example.rffinalproject;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.content.Context;
import android.widget.Button;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.TextView;
import android.Manifest;
import android.media.ExifInterface;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.FirebaseStorage;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.io.FileOutputStream;

//Robert Fernald
//CSCI 4540 Final Project

//You can test the load button by typing in "i1", "i2", or "i3" and tapping load
//These images are saved on Firebase, I have also included them in the drawable folder

//The camera button is working, but I have to plug in my physical phone to test the camera
//Text extracted from the image is output in the "Recognition Result" area

//The save button stopped working, I commented out the code to prevent crashes

public class MainActivity extends AppCompatActivity {

    private StorageReference storageReference;
    private Button sample;
    private Button saveItems;
    private Button camera;
    private EditText etReceiptName;
    private TextView tvRecognitionResult;
    private StorageReference imageFolder;
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final int REQUEST_IMAGE_CAPTURE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // Initialize Firebase Storage
        storageReference = FirebaseStorage.getInstance().getReference();
        FirebaseStorage storage = FirebaseStorage.getInstance("gs://rf-final-project.appspot.com/");
        imageFolder = storage.getReference().child("receiptImages");

        // Initialize UI elements
        sample = findViewById(R.id.btnFromSample);
        camera = findViewById(R.id.btnFromCamera);
        saveItems = findViewById(R.id.btnSaveItems);
        tvRecognitionResult = findViewById(R.id.tvRecognitionResult);
        etReceiptName = findViewById(R.id.etReceiptName);

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }
    //Method for Camera button
    public void onCameraButtonClick(View view) {
        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            // Open the camera
            // You can replace "CameraReceipt" with the actual receipt name entered by the user
            dispatchTakePictureIntent("CameraReceipt");
        } else {
            // Request camera permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    //Get image from Firebase folder and convert to InputImage
    private void processImage(String fileName, Context context, Uri uri) {
        String fileUrl = "gs://rf-final-project.appspot.com/receiptImages/" + fileName;
        StorageReference imageRef = FirebaseStorage.getInstance().getReferenceFromUrl(fileUrl);

        // Convert the Uri to InputImage
        try {
            InputImage image = InputImage.fromFilePath(context, uri);
            if (image != null) {
                recognizeText(image);
            }
            Log.d("Camera", "processImage");
        } catch (IOException e) {
            e.printStackTrace();
            // Handle the exception (e.g., display a Toast)
            Toast.makeText(context, "Error processing image", Toast.LENGTH_SHORT).show();
        }
    }

    //Uses ML-Kit to extract text from an input image
    //MLKit documentation: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
    private void recognizeText(InputImage image) {

        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        Task<Text> result =
                recognizer.process(image)
                        .addOnSuccessListener(new OnSuccessListener<Text>() {
                            @Override
                            public void onSuccess(Text visionText) {
                                StringBuilder recognizedText = new StringBuilder();
                                for (Text.TextBlock block : visionText.getTextBlocks()) {
                                    Rect boundingBox = block.getBoundingBox();
                                    Point[] cornerPoints = block.getCornerPoints();
                                    String text = block.getText();
                                    recognizedText.append(text).append("\n");
                                }

                                // Update the TextView with the recognized text
                                runOnUiThread(() -> {
                                    tvRecognitionResult.setText(recognizedText.toString().trim());
                                });
                            }
                        })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        // Task failed with an exception
                                    }
                                });
    }

    //Method for Load button, it started with a different name.
    public void onSampleButtonClick(View view) {
        // Get the entered receipt name from the EditText
        String receiptName = etReceiptName.getText().toString();
        String fileName = receiptName + ".jpg";

        // Create a reference to the image in Firebase Storage
        StorageReference imageRef = FirebaseStorage.getInstance().getReference()
                .child("receiptImages")
                .child(fileName);

        // Download the image to a local file
        // Inside onSampleButtonClick method
        try {
            File localFile = File.createTempFile("images", "jpg");
            imageRef.getFile(localFile).addOnSuccessListener(taskSnapshot -> {
                // The image has been downloaded successfully
                Uri localUri = Uri.fromFile(localFile);
                processImage(fileName, this, localUri);
            }).addOnFailureListener(exception -> {
                // Handle any errors that occurred while downloading the image
                exception.printStackTrace();
                Toast.makeText(this, "Error downloading image from Firebase Storage", Toast.LENGTH_SHORT).show();
            });
        } catch (IOException e) {
            e.printStackTrace();
            // Exception handling
            Toast.makeText(this, "Error creating local file", Toast.LENGTH_SHORT).show();
        }
    }

    //Used to save the camera image with a name that the user enters
    private void dispatchTakePictureIntent(String receiptName) {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Pass the receipt name as an extra to the camera intent
            takePictureIntent.putExtra("receiptName", receiptName);
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            if (data != null && data.getExtras() != null) {
                // The captured image is available in the 'data' Intent
                Bundle extras = data.getExtras();
                Bitmap imageBitmap = (Bitmap) extras.get("data");

                if (imageBitmap != null) {
                    // Convert the Bitmap to InputImage
                    InputImage image = InputImage.fromBitmap(imageBitmap, 0);

                    // Call recognizeText on the captured image
                    recognizeText(image);

                    // Save the Bitmap to a file for uploading
                    File imageFile = saveBitmapToFile(imageBitmap);

                    // Get the entered receipt name from the EditText
                    String receiptName = etReceiptName.getText().toString();

                    // Call onSaveButtonClick to upload the image to Firebase
                    //saveToFirebase(Uri.fromFile(imageFile), receiptName);
                } else {
                    // Handle the case where the captured image is null
                }
            } else {
                // Handle the case where data Intent or its extras are null
            }
        }
    }
    //Save is not working, it was working but caused intermittent crashes
    //So I disabled it
    private File saveBitmapToFile(Bitmap bitmap) {
        // Create a file in the app's cache directory
        File file = new File(getCacheDir(), "captured_image.jpg");

        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            // Compress the Bitmap to JPEG format
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return file;
    }

    // Account for camera rotation
    private int getRotationInDegrees(int exifOrientation) {
        // Convert exif orientation to degrees
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }


    public void onSaveButtonClick(View view) {
        //This method broken and prevented the camera from working so I commented it out

//        if (capturedImageUri == null || TextUtils.isEmpty(capturedReceiptName)) {
//            Log.e("Firebase", "Image URI or receipt name is null");
//            return;
//        }
//        saveToFirebase(capturedImageUri, capturedReceiptName);
    }

    //Not currently working since onSaveButtonClick is broken

//    private void saveToFirebase(Uri imageUri, String receiptName) {
//        // Ensure the imageUri is not null
//        if (imageUri == null) {
//            Log.e("Firebase", "Image URI is null");
//            return;
//        }
//
//        // Create a reference to the root of your Firebase Storage
//        StorageReference storageRef = FirebaseStorage.getInstance().getReference();
//
//        // Create a reference to the folder where you want to save the image
//        StorageReference imagesRef = storageRef.child("receiptImages");
//
//        // Create a reference to the image file using the entered receipt name as the filename
//        StorageReference imageRef = imagesRef.child(receiptName + ".jpg");
//
//        // Upload the file to Firebase Storage
//        imageRef.putFile(imageUri)
//                .addOnSuccessListener(taskSnapshot -> {
//                    // Image uploaded successfully
//                    Log.d("Firebase", "Image uploaded successfully");
//                })
//                .addOnFailureListener(exception -> {
//                    // Handle any errors that occurred while uploading the image
//                    exception.printStackTrace();
//                    Log.e("Firebase", "Error uploading image to Firebase Storage");
//                });
//    }
}