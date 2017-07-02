package com.example.owner.mymapapplication;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

// Religious Tourism app - finds religious places and other areas of interest
// Cannot use Place Picker for this app since I only want specific places to be displayed
// Apparently ability to filter results in Place Picker is an open issue:
// https://code.google.com/p/gmaps-api-issues/issues/detail?id=8484

public class MyMapApplication extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    //instance variables for Marker icon drawable resources
    private int churchIcon, restaurantIcon, synagogueIcon, templeIcon, mosqueIcon, meIcon;
    //user marker
    private Marker userMarker;
    //places of interest
    private Marker[] placeMarkers;
    //max places returned from Google
    private final int MAX_PLACES = 20;
    //marker options
    private MarkerOptions[] places;
    //the map
    GoogleMap googleMap;
    // the client
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;
    protected static final int REQUEST_CHECK_SETTINGS = 0x1;
    Location crntLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_map);

        // Create an instance of GoogleAPIClient.
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        // check for map and get it
        if(googleMap==null) {
            MapFragment googleMap = (MapFragment) getFragmentManager()
                    .findFragmentById(R.id.my_map);
            googleMap.getMapAsync(this);
        }

        // Create location request
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        //Check the settings for location services
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        builder.setAlwaysShow(true);
        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                final LocationSettingsStates state = result.getLocationSettingsStates();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        // All location settings are satisfied. update locations
                        // Check for availability of network
                        if (isNetworkAvailable()) {
                            updatePlaces();
                        } else{
                            Toast.makeText(MyMapApplication.this,"Please check network connection",Toast.LENGTH_LONG).show();
                        }
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied. Showing the user a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(MyMapApplication.this, REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException ignored) {
                            // Oh - well!!
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied.
                        break;
                }
            }
        });
    }

    // Handle location settings requests
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        final LocationSettingsStates states = LocationSettingsStates.fromIntent(data);
        switch (requestCode) {
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        // All required changes were successfully made
                        if (isNetworkAvailable()) {
                            updatePlaces();
                        }
                        break;
                    case Activity.RESULT_CANCELED:
                        // The user was asked to change settings, but chose not to
                        break;
                    default:
                        break;
                }
                break;
        }
    }

    // Set up my map
    @Override
    public void onMapReady(GoogleMap _googleMap) {
        googleMap = _googleMap;
        googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        googleMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));
    }

    // Update my markers
   private void updatePlaces() {
       //get drawable IDs
       templeIcon = R.drawable.temple;
       restaurantIcon = R.drawable.restaurant;
       churchIcon = R.drawable.church;
       mosqueIcon = R.drawable.mosque;
       synagogueIcon = R.drawable.synagogue;
       meIcon = R.drawable.user;
       placeMarkers = new Marker[MAX_PLACES];
       //get last location
       double lat = 0;
       double lng = 0;
       try {
           mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
           if (mLastLocation != null) {
               lat = mLastLocation.getLatitude();
               lng = mLastLocation.getLongitude();
               crntLocation = new Location("crntlocation");
               crntLocation.setLatitude(lat);
               crntLocation.setLongitude(lng);
           }
           //create LatLng
           LatLng lastLatLng = new LatLng(lat, lng);
           //remove existing marker - else I get multiple markers on location changed
           if (userMarker != null) {
               userMarker.remove();
           }
           userMarker = googleMap.addMarker(new MarkerOptions()
                   .position(lastLatLng)
                   .title("You are here")
                   .icon(BitmapDescriptorFactory.fromResource(meIcon)));

           //move to location
           googleMap.animateCamera(CameraUpdateFactory.newLatLng(lastLatLng), 3000, null);
           CameraPosition cameraPosition = new CameraPosition.Builder()
                   .target(new LatLng(lat, lng))       // Sets the center of the map to current location
                   .zoom(17)                           // Sets the zoom
                   .bearing(90)                        // Sets the orientation of the camera
                   .tilt(40)                           // Sets the tilt of the camera
                   .build();
           googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
           //build the places query string
           String placesSearchStr = "https://maps.googleapis.com/maps/api/place/nearbysearch/" +
                   "json?location=" + lat + "," + lng +
                   "&radius=2000&sensor=true" +
                   "&types=church|hindu_temple|mosque|synagogue|restaurant" +
                   "&key=AIzaSyACPfHY0qur4T8kGpLBQNHAEn7YIXNAmUE";//ADD Browser KEY

           //execute query
           new GetPlaces().execute(placesSearchStr);

       } catch (SecurityException e) {
           //  there is a problem with the gps
           Toast.makeText(MyMapApplication.this,"Please check connection",Toast.LENGTH_LONG).show();
       }
   }

    private class GetPlaces extends AsyncTask<String, Void, String> {

        private ProgressDialog progressDialog = new ProgressDialog(MyMapApplication.this);
        // Alert user that data is being fetched
        protected void onPreExecute() {
            progressDialog.setMessage("Loading data from Google Places...");
            progressDialog.show();
            progressDialog.setCancelable(false);
        }

        @Override
        protected String doInBackground(String... placesURL) {
            //fetch places
            //build result as string
            StringBuilder placesBuilder = new StringBuilder();
            //process search parameter string(s)
            for (String placeSearchURL : placesURL) {
                try {
                    URL url = new URL(placeSearchURL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setDoInput(true);
                    conn.connect();
                    int placeSearchStatus = conn.getResponseCode();
                    //only carry on if response is OK
                    if (placeSearchStatus == 200) {
                        BufferedReader placesReader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String lineIn;
                        while ((lineIn = placesReader.readLine()) != null) {
                            placesBuilder.append(lineIn);
                        }
                        conn.disconnect();
                    } else {
                        Toast.makeText(MyMapApplication.this,"Please check connection",Toast.LENGTH_LONG).show();
                    }
                }
                catch(Exception e){
                    Toast.makeText(MyMapApplication.this,"Please check connection",Toast.LENGTH_LONG).show();
                }
            }
            return placesBuilder.toString();
        }

        //process data retrieved from doInBackground
        protected void onPostExecute(String result) {
            //parse place data returned from Google Places
            //remove existing markers
            if(placeMarkers!=null){
                for(int i = 0; i < placeMarkers.length; i++){
                    if(placeMarkers[i]!=null)
                        placeMarkers[i].remove();
                }
            }
            try {
                //create JSONObject, pass string returned from doInBackground
                JSONObject resultObject = new JSONObject(result);
                //get "results" array
                JSONArray placesArray = resultObject.getJSONArray("results");
                //marker options for each place returned
                places = new MarkerOptions[placesArray.length()];
                //loop through places
                for (int p=0; p<placesArray.length(); p++) {
                    //parse each place
                    boolean missingValue=false;
                    LatLng placeLL=null;
                    String placeName="";
                    float distance = 0;
                    String outputString = "";
                    int currIcon = meIcon;
                    try{
                        //attempt to retrieve place data values
                        missingValue=false;
                        //get place at this index
                        JSONObject placeObject = placesArray.getJSONObject(p);
                        //get location section
                        JSONObject loc = placeObject.getJSONObject("geometry")
                                .getJSONObject("location");
                        //read lat lng
                        Double thisLat = Double.valueOf(loc.getString("lat"));
                        Double thisLong = Double.valueOf(loc.getString("lng"));
                        placeLL = new LatLng(Double.valueOf(loc.getString("lat")),
                                Double.valueOf(loc.getString("lng")));
                        //get types
                        JSONArray types = placeObject.getJSONArray("types");
                        //loop through types
                        for(int t=0; t<types.length(); t++){
                            //what type is it
                            String thisType=types.get(t).toString();
                            //check for particular types - set icons
                            if(thisType.contains("church")){
                                currIcon = churchIcon;
                                break;
                            }
                            else if(thisType.contains("hindu_temple")){
                                currIcon = templeIcon;
                                break;
                            }
                            else if(thisType.contains("synagogue")){
                                currIcon = synagogueIcon;
                                break;
                            }
                            else if(thisType.contains("mosque")){
                                currIcon = mosqueIcon;
                                break;
                            }
                            else if(thisType.contains("restaurant")){
                                currIcon = restaurantIcon;
                                break;
                            }
                        }
                        // get the name of the identified place
                        placeName = placeObject.getString("name");
                        //calculate distance to places in meters
                        Location newLocation=new Location("newlocation");
                        newLocation.setLatitude(thisLat);
                        newLocation.setLongitude(thisLong);
                        distance = crntLocation.distanceTo(newLocation);
                        // format the output so that it looks nice!
                        outputString = String.format(java.util.Locale.US, "%.2f", distance)+ " meters distance";
                    }
                    catch(JSONException jse){
                        Log.v("PLACES", "missing value");
                        missingValue=true;
                        jse.printStackTrace();
                    }
                    //if values missing - don't display
                    if(missingValue) {
                        places[p] = null;
                    }else {
                        // add the marker with name and distance
                        places[p] = new MarkerOptions()
                                .position(placeLL)
                                .title(placeName)
                                .icon(BitmapDescriptorFactory.fromResource(currIcon))
                                .snippet(outputString);
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            if(places!=null && placeMarkers!=null){
                for(int j=0; j<places.length && j<placeMarkers.length; j++){
                    //will be null if a value was missing
                    if(places[j]!=null) {
                        placeMarkers[j] = googleMap.addMarker(places[j]);
                    }
                }
            }
            // finish with the progress dialog
            progressDialog.dismiss();
        }
    }


    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }


    @Override
    protected void onResume() {
        super.onResume();
        if(googleMap !=null){
            try {
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            } catch (SecurityException e) {

            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(googleMap !=null){
            try{
                LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            } catch (SecurityException e) {

            }
        }
    }

    //location listener functions
    @Override
    public void onLocationChanged(Location location) {
        Log.v("MyMapApplication", "location changed");
        if (isNetworkAvailable()) {
           updatePlaces();
        } else{
            Toast.makeText(MyMapApplication.this,"Please check connection",Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Toast.makeText(this,"Connected",Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Toast.makeText(this,"Connection Suspended",Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Toast.makeText(this,"Connection Failed", Toast.LENGTH_SHORT).show();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }

}
