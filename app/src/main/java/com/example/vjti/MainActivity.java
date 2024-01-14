package com.example.vjti;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.location.Location;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.location.Geocoder;
import android.location.Address;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private BottomSheetDialog bottomSheetDialog;
    private FusedLocationProviderClient fusedLocationClient;
    private SearchView locationEditText, destinationSearchView;
    private Marker locationMarker, destinationMarker;
    private LatLng start, end;
    private Polyline currentPolyline;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationEditText = findViewById(R.id.location);
        destinationSearchView = findViewById(R.id.destinationSearchView);

        destinationSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                geocodeDestination(query, latLng -> {
                    end = latLng;
                    checkAndDrawRoute();
                });
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        // Setting up the listener for the SearchView
        locationEditText.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                geocodeLocation(query, latLng -> {
                    start = latLng;
                    checkAndDrawRoute();
                });
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    private void geocodeLocation(String query, GeocodeCallback callback) {
        Geocoder geocoder = new Geocoder(this);
        new Thread(() -> {
            try {
                List<Address> addresses = geocoder.getFromLocationName(query, 1);
                runOnUiThread(() -> {
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        LatLng searchedLatLng = new LatLng(address.getLatitude(), address.getLongitude());
                        start = searchedLatLng;

                        // Check if the existing marker is the "Your Location" marker and remove it
                        if (locationMarker != null && locationMarker.getTitle().equals("Your Location")) {
                            locationMarker.remove();
                        }

                        // Add a new marker for the searched location
                        locationMarker = mMap.addMarker(new MarkerOptions().position(searchedLatLng).title("Searched Location"));
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(searchedLatLng, 16.0f));

                        if (callback != null) {
                            callback.onGeocodeComplete(searchedLatLng);
                        }
                    } else {
                        Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Error finding location", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }


    private void geocodeDestination(String query, GeocodeCallback callback) {
        if (!query.isEmpty()) {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            new Thread(() -> {
                try {
                    List<Address> addresses = geocoder.getFromLocationName(query, 1);
                    runOnUiThread(() -> {
                        if (addresses != null && !addresses.isEmpty()) {
                            Address address = addresses.get(0);
                            LatLng destinationLatLng = new LatLng(address.getLatitude(), address.getLongitude());
                            end = destinationLatLng;

                            if (destinationMarker != null) {
                                destinationMarker.remove();
                            } destinationMarker = mMap.addMarker(new MarkerOptions().position(destinationLatLng).title("Destination"));
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(destinationLatLng, 15));

                            if (callback != null) {
                                callback.onGeocodeComplete(destinationLatLng);
                            }
                        } else {
                            Toast.makeText(MainActivity.this, "Location not found", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error finding location", Toast.LENGTH_SHORT).show());
                }
            }).start();
        }
    }


    interface GeocodeCallback{
        void onGeocodeComplete(LatLng latLng);
    }

    private void checkAndDrawRoute() {
        if (start != null && end != null) {
            drawRoute(start, end);
        }
    }

    private void drawRoute(LatLng start, LatLng end) {
        if (currentPolyline != null) {
            currentPolyline.remove();
        }
        String url = getOpenRouteServiceUrl(start, end);
        new FetchRouteData().execute(url);
    }

    private String getOpenRouteServiceUrl(LatLng start, LatLng end) {
        String str_origin = start.longitude + "," + start.latitude;
        String str_dest = end.longitude + "," + end.latitude;
        String url = "https://api.openrouteservice.org/v2/directions/driving-car?api_key=5b3ce3597851110001cf6248e4e56cd72eff4f51ad6bd3024be994de&start=" + str_origin + "&end=" + str_dest;
        Log.d("URL", "OpenRouteService URL: " + url);
        return url;
    }

    private class FetchRouteData extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... url) {
            try {
                Log.d("FetchRouteData", "Requesting route data");
                URL directionUrl = new URL(url[0]);
                HttpURLConnection connection = (HttpURLConnection) directionUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                return builder.toString();
            } catch (Exception e) {
                Log.e("FetchRouteData", "Error: " + e.getMessage(), e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (result != null) {
                Log.d("FetchRouteData", "Response: " + result);
                try {
                    JSONObject jsonObject = new JSONObject(result);
                    JSONArray features = jsonObject.getJSONArray("features");
                    PolylineOptions polylineOptions = null;
                    if (features.length() > 0) {
                        JSONObject feature = features.getJSONObject(0);
                        JSONObject geometry = feature.getJSONObject("geometry");
                        JSONArray coordinates = geometry.getJSONArray("coordinates");

                        List<LatLng> routePoints = new ArrayList<>();
                        for (int i = 0; i < coordinates.length(); i++) {
                            JSONArray coordinate = coordinates.getJSONArray(i);
                            double longitude = coordinate.getDouble(0);
                            double latitude = coordinate.getDouble(1);
                            routePoints.add(new LatLng(latitude, longitude));
                        }

                        if (!routePoints.isEmpty()) {
                            polylineOptions = new PolylineOptions()
                                    .addAll(routePoints)
                                    .width(10)
                                    .color(Color.BLUE);
                        }
                    }

                    // Remove the previous polyline if it exists
                    if (currentPolyline != null) {
                        currentPolyline.remove();
                    }

                    // Draw the new polyline
                    if (polylineOptions != null) {
                        currentPolyline = mMap.addPolyline(polylineOptions);
                        openBottomSheetWithInfo(start, end);
                    }
                } catch (Exception e) {
                    Log.e("FetchRouteData", "Error parsing JSON: " + e.getMessage(), e);
                }
            } else {
                Log.e("FetchRouteData", "No result received");
            }
        }


    }

    private float calculateDistance(LatLng start, LatLng end) {
        float[] results = new float[1];
        Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results);
        return results[0] / 1000; // returns distance in kilometers
    }

    private void openBottomSheetWithInfo(LatLng start, LatLng end) {
        float distance = calculateDistance(start, end);

        TaxiInfo autoRickshaw = new TaxiInfo("Auto Rickshaw", calculatePrice("Auto Rickshaw", distance), calculateTime("Auto Rickshaw", distance));
        TaxiInfo economyTaxi = new TaxiInfo("Economy Taxi", calculatePrice("Economy Taxi", distance), calculateTime("Economy Taxi", distance));
        TaxiInfo sedanTaxi = new TaxiInfo("Sedan Taxi", calculatePrice("Sedan Taxi", distance), calculateTime("Sedan Taxi", distance));
        TaxiInfo suvTaxi = new TaxiInfo("SUV Taxi", calculatePrice("SUV Taxi", distance), calculateTime("SUV Taxi", distance));

        showBottomSheet(autoRickshaw, economyTaxi, sedanTaxi, suvTaxi);
    }


    public class TaxiInfo {
        String name;
        float price;
        int time;

        public TaxiInfo(String name, float price, int time) {
            this.name = name;
            this.price = price;
            this.time = time;
        }

        // Getters
        public String getName() { return name; }
        public float getPrice() { return price; }
        public int getTime() { return time; }
    }

    private float calculatePrice(String transportMode, float distance) {
        float rate;
        switch (transportMode) {
            case "Auto Rickshaw":
                rate = 15; // per kilometer
                break;
            case "Economy Taxi":
                rate = 20;
                break;
            case "Sedan Taxi":
                rate = 25;
                break;
            case "SUV Taxi":
                rate = 30;
                break;
            default:
                rate = 20;
        }
        return distance * rate;
    }

    private int calculateTime(String transportMode, float distance) {
        float speed;
        switch (transportMode) {
            case "Auto Rickshaw":
                speed = 30; // km/h
                break;
            case "Economy Taxi":
                speed = 40;
                break;
            case "Sedan Taxi":
                speed = 45;
                break;
            case "SUV Taxi":
                speed = 40;
                break;
            default:
                speed = 40;
        }
        return (int) ((distance / speed) * 60); // time in minutes
    }

    private void showBottomSheet(TaxiInfo autoRickshaw, TaxiInfo economyTaxi, TaxiInfo sedanTaxi, TaxiInfo suvTaxi) {
        bottomSheetDialog = new BottomSheetDialog(this);
        View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_layout, null);

        // Auto Rickshaw
        TextView autoRickshawName = bottomSheetView.findViewById(R.id.autoRickshawName);
        TextView autoRickshawPrice = bottomSheetView.findViewById(R.id.autoRickshawPrice);
        TextView autoRickshawTime = bottomSheetView.findViewById(R.id.autoRickshawTime);

        autoRickshawName.setText(autoRickshaw.getName());
        autoRickshawPrice.setText("Price: ₹" + autoRickshaw.getPrice());
        autoRickshawTime.setText("Time: " + autoRickshaw.getTime() + " mins");

        // Economy Taxi
        TextView economyTaxiName = bottomSheetView.findViewById(R.id.Economy_Taxi);
        TextView economyTaxiPrice = bottomSheetView.findViewById(R.id.Economy_Price);
        TextView economyTaxiTime = bottomSheetView.findViewById(R.id.EconomyTime);

        economyTaxiName.setText(economyTaxi.getName());
        economyTaxiPrice.setText("Price: ₹" + economyTaxi.getPrice());
        economyTaxiTime.setText("Time: " + economyTaxi.getTime() + " mins");

        // Sedan Taxi
        TextView sedanTaxiName = bottomSheetView.findViewById(R.id.SedanName);
        TextView sedanTaxiPrice = bottomSheetView.findViewById(R.id.SedanPrice);
        TextView sedanTaxiTime = bottomSheetView.findViewById(R.id.SedanTime);

        sedanTaxiName.setText(sedanTaxi.getName());
        sedanTaxiPrice.setText("Price: ₹" + sedanTaxi.getPrice());
        sedanTaxiTime.setText("Time: " + sedanTaxi.getTime() + " mins");

        // SUV Taxi
        TextView suvTaxiName = bottomSheetView.findViewById(R.id.SUVName);
        TextView suvTaxiPrice = bottomSheetView.findViewById(R.id.SUVPrice);
        TextView suvTaxiTime = bottomSheetView.findViewById(R.id.SUVTime);

        suvTaxiName.setText(suvTaxi.getName());
        suvTaxiPrice.setText("Price: ₹" + suvTaxi.getPrice());
        suvTaxiTime.setText("Time: " + suvTaxi.getTime() + " mins");

        bottomSheetDialog.setContentView(bottomSheetView);
        bottomSheetDialog.show();
    }




    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);

            // Get the last known location and place a marker
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                            setAddressInEditText(userLocation);
                            mMap.addMarker(new MarkerOptions().position(userLocation).title("Your Location"));
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 12));
                        }
                    });

        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);

            // Coordinates for New Delhi, India
            LatLng india = new LatLng(28.6139, 77.2090);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(india, 5));
        }
    }

    private void setAddressInEditText(LatLng userLocation) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(userLocation.latitude, userLocation.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                String address = addresses.get(0).getAddressLine(0);
//                locationEditText.setText(address);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        mMap.setMyLocationEnabled(true);
                        // Get the last known location and place a marker
                        fusedLocationClient.getLastLocation()
                                .addOnSuccessListener(this, location -> {
                                    if (location != null) {
                                        if (locationMarker != null) {
                                            locationMarker.remove();
                                        }
                                        LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                                        setAddressInEditText(userLocation);
                                        updateSearchViewWithLocation(location);
                                        mMap.addMarker(new MarkerOptions().position(userLocation).title("Your Location"));
                                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 16));
                                        start = userLocation;

                                    }
                                });
                    }
                } else {
                    LatLng india = new LatLng(28.6139, 77.2090);
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(india, 5));
                }
            }
        }
    }

    private void updateSearchViewWithLocation(Location location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                String address = addresses.get(0).getAddressLine(0);
                locationEditText.setQuery(address, false);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sos(View view){
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:103")));
    }
}
