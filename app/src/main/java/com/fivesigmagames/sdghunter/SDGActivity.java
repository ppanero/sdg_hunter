package com.fivesigmagames.sdghunter;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.design.widget.TabLayout;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.fivesigmagames.sdghunter.model.ShareItem;
import com.fivesigmagames.sdghunter.model.repository.ShareItemRepository;
import com.fivesigmagames.sdghunter.services.LocationService;
import com.fivesigmagames.sdghunter.view.AboutFragment;
import com.fivesigmagames.sdghunter.view.HomeFragment;
import com.fivesigmagames.sdghunter.view.MapFragment;
import com.fivesigmagames.sdghunter.view.PreviewActivity;
import com.fivesigmagames.sdghunter.view.ShareActivity;
import com.fivesigmagames.sdghunter.view.ShareFragment;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import static android.os.Environment.getExternalStoragePublicDirectory;

public class SDGActivity extends AppCompatActivity implements HomeFragment.OnHomeFragmentInteractionListener,
        MapFragment.OnFragmentInteractionListener, ShareFragment.OnShareFragmentInteractionListener,
        AboutFragment.OnAboutFragmentInteractionListener {

    // CONSTANTS
    private static final String SAVING_PICTURE_ERROR_MESSAGE = "Unexpected error when saving picture";
    private static final String DIRECTORY_CREATION_ERROR_MESSAGE = "Unxpected error when creating directory";
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int SHOW_PREVIEW_CAPTURE = 10;
    private static final int RESULT_PHOTO_RETAKE = 0;
    private static final int RESULT_PHOTO_SHARE = 1; // Save not needed, saved by default
    private static final int TAKEN_DIR = 1;
    private static final int DOWNLOAD_DIR = 2;

    // VARS
    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;
    private LocationReceiver locationReceiver;
    private String mCurrentPhotoPath;
    private Location mCurrentLocation;
    private boolean mLocationEnabled;
    private ShareItemRepository mShareItemRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sdh);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_sdh, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the HomeFragment/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        //Register BroadcastReceiver
        //to receive event from our service
        super.onStart();
        mLocationEnabled = true;
        String locationProviders = Settings.Secure.getString(getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
        if (locationProviders == null || locationProviders.equals("")) {
            mLocationEnabled = false;
            buildAlertMessageNoGps();
        }

        locationReceiver = new LocationReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(LocationService.LOCATION_UPDATE);
        registerReceiver(locationReceiver, intentFilter);

        //Start our own service
        Intent intent = new Intent(this, LocationService.class);
        startService(intent);

        mShareItemRepository = new ShareItemRepository(this);
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it? If you do not enable. " +
                "You will not be able to take pictures with the app")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        mLocationEnabled = false;
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(locationReceiver);
        stopService(new Intent(this, LocationService.class));
        super.onStop();
    }

    // Interface Methods
    @Override
    public void onFragmentInteraction(Uri uri) {

    }

    // Home Fragment
    @Override
    public void activateCamera(){

        String locationProviders = Settings.Secure.getString(getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
        if (locationProviders == null || locationProviders.equals("")) {
            mLocationEnabled = false;
            buildAlertMessageNoGps();
        }

        if(mLocationEnabled) { // Only take a picture if the location is activated
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {

                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    // Error occurred while creating the File
                    Toast.makeText(this, SAVING_PICTURE_ERROR_MESSAGE, Toast.LENGTH_LONG).show();
                }
                // Continue only if the File was successfully created
                if (photoFile != null) {
                    Uri photoURI = FileProvider.getUriForFile(this, "com.fivesigmagames.sdghunter.fileprovider",
                            photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                } else {

                    Toast.makeText(this, DIRECTORY_CREATION_ERROR_MESSAGE, Toast.LENGTH_LONG).show();
                }

            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            updateGallery(mCurrentPhotoPath);
            // Start preview
            Intent previewIntent = new Intent(SDGActivity.this, PreviewActivity.class);
            previewIntent.putExtra("pic_path", mCurrentPhotoPath);
            startActivityForResult(previewIntent, SHOW_PREVIEW_CAPTURE);
        }
        else if(requestCode == SHOW_PREVIEW_CAPTURE){
            if(resultCode == RESULT_PHOTO_RETAKE){
                Bundle extras = data.getExtras();
                String picPath = extras.getString("pic_path");
                deleteFileFromMediaStore(getContentResolver(), new File(picPath));
                this.activateCamera();
            }
            else if(resultCode == RESULT_PHOTO_SHARE){
                Bundle extras = data.getExtras();
                Intent intent = new Intent(SDGActivity.this, ShareActivity.class);
                String picPath = extras.getString("pic_path");
                intent.putExtra("pic_path", picPath);
                savePhotoEntryInDb(picPath);
                startActivity(intent);
            }
            else {
                savePhotoEntryInDb(data.getExtras().getString("pic_path"));
            }
        }
    }

    public static void deleteFileFromMediaStore(final ContentResolver contentResolver, final File file) {
        String canonicalPath;
        try {
            canonicalPath = file.getCanonicalPath();
        } catch (IOException e) {
            canonicalPath = file.getAbsolutePath();
        }
        final Uri uri = MediaStore.Files.getContentUri("external");
        final int result = contentResolver.delete(uri,
                MediaStore.Files.FileColumns.DATA + "=?", new String[] {canonicalPath});
        if (result == 0) {
            final String absolutePath = file.getAbsolutePath();
            if (!absolutePath.equals(canonicalPath)) {
                contentResolver.delete(uri,
                        MediaStore.Files.FileColumns.DATA + "=?", new String[]{absolutePath});
            }
        }
    }

    private void savePhotoEntryInDb(String picPath) {
        String[] parts = picPath.split(File.separator);
        mShareItemRepository.insert(new ShareItem(parts[parts.length-1], picPath,
                mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()));
    }

    private void updateGallery(String picPath){
        //update gallery
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File pic = new File(picPath);
        Uri contentUri = Uri.fromFile(pic);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        File storageDir = null, image = null;
        if ((storageDir = checkSDGHunterDirectory(TAKEN_DIR)) != null) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(new Date());
            String imageFileName = "JPEG_" + timeStamp;
            image = File.createTempFile(
                    imageFileName,  /* prefix */
                    ".jpeg",         /* suffix */
                    storageDir      /* directory */
            );
            mCurrentPhotoPath = image.getAbsolutePath();
        }
        // Save a file: path for use with ACTION_VIEW intents
        return image;
    }

    private File checkSDGHunterDirectory(int dir){
        String sdgPictures = getResources().getString(R.string.sdg_pictures_path);
        File sdgDir = new File(getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), sdgPictures);
        if (!sdgDir.exists()) {
            sdgDir.mkdirs();
        }
        String sdgSubDir = null;
        if(dir == TAKEN_DIR){
            sdgSubDir = getResources().getString(R.string.sdg_taken_pictures_path);
        }
        else if(dir == DOWNLOAD_DIR){
            sdgSubDir = getResources().getString(R.string.sdg_download_pictures_path);
        }
        File subDir = null;
        if(sdgSubDir != null) {
            subDir = new File(sdgDir, sdgSubDir);
            if (!subDir.exists()) {
                subDir.mkdirs();
            }
            return subDir;
        }
        return subDir;
    }

    // Share Fragment
    @Override
    public void sharePicture(int position) {
        ShareFragment fragment = (ShareFragment) getSupportFragmentManager().findFragmentByTag(getFragementTag(2));
        ShareItem item = fragment.getShareItem(position);

        //Create intent
        Intent intent = new Intent(SDGActivity.this, ShareActivity.class);
        intent.putExtra("pic_path", item.getFullPath());
        //Start details activity
        startActivity(intent);
    }

    private String getFragementTag(int position) {
        return "android:switcher:" + mViewPager.getId() + ":" + position;
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            switch (position){
                case 0:
                    return HomeFragment.newInstance();
                case 1:
                    return MapFragment.newInstance();
                case 2:
                    return ShareFragment.newInstance(getSDGImages());
                case 3:
                    return AboutFragment.newInstance();
            }
            return null;
        }

        @Override
        public int getCount() {
            // Show 4 total pages.
            return 4;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "HOME";
                case 1:
                    return "MAP";
                case 2:
                    return "SHARE";
                case 3:
                    return "ABOUT";
            }
            return null;
        }
    }

    private ArrayList<ShareItem> getSDGImages() {

        ArrayList<ShareItem> files = new ArrayList();// list of file paths
        String sdgPictures = getResources().getString(R.string.sdg_pictures_path).concat(File.separator).concat(
                getResources().getString(R.string.sdg_taken_pictures_path)
        );
        File file= new File(getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),sdgPictures);

        if (file.isDirectory()) {
            File[] listFile = file.listFiles();
            for (int i = 0; i < listFile.length; i++) {
                ShareItem item = mShareItemRepository.findByName(listFile[i].getName());
                if(item != null){
                    item.setFullPath(listFile[i].getAbsolutePath());
                    files.add(item);
                }
                else {
                    Log.e("SDG Hunter", "An entry in the db should exist for " + listFile[i].getName());
                }
            }
        }
        return files;
    }

    private class LocationReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            mCurrentLocation = intent.getParcelableExtra("LOCATION");
            Log.d("SDG Activity","Current location: lat - " + mCurrentLocation.getLatitude() +
                    " long -" + mCurrentLocation.getLongitude());
        }
    }
}
