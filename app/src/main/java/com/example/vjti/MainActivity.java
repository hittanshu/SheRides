package com.example.vjti;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.location.Location;
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
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.location.Geocoder;
import android.location.Address;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private BottomSheetDialog bottomSheetDialog;
    private FusedLocationProviderClient fusedLocationClient;
    private SearchView locationEditText, destinationSearchView;
    private Marker locationMarker, destinationMarker;


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
                geocodeDestination(query);
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
                geocodeLocation(query);
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

    private void geocodeDestination(String query) {
        if (!query.isEmpty()) {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            new Thread(() -> {
                try {
                    List<Address> addresses = geocoder.getFromLocationName(query, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        LatLng destinationLatLng = new LatLng(address.getLatitude(), address.getLongitude());

                        runOnUiThread(() -> {
                            if (destinationMarker != null) {
                                // Move the existing marker
                                destinationMarker.setPosition(destinationLatLng);
                            } else {
                                // Create a new marker
                                destinationMarker = mMap.addMarker(new MarkerOptions().position(destinationLatLng).title("Destination"));
                            }
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(destinationLatLng, 15));
                        });
                    } else {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Location not found", Toast.LENGTH_SHORT).show());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error finding location", Toast.LENGTH_SHORT).show());
                }
            }).start();
        }
    }

    private void geocodeLocation(String query) {
        Geocoder geocoder = new Geocoder(this);
        try {
            List<Address> addresses = geocoder.getFromLocationName(query, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                LatLng searchedLatLng = new LatLng(address.getLatitude(), address.getLongitude());

                runOnUiThread(() -> {
                    if (locationMarker != null) {
                        // Move the existing marker
                        locationMarker.setPosition(searchedLatLng);
                    } else {
                        // Create a new marker
                        locationMarker = mMap.addMarker(new MarkerOptions().position(searchedLatLng).title("Your Location"));
                    }
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(searchedLatLng, 16.0f));
                });
            } else {
                Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleDestinationSearch(String destination) {
        if (!destination.isEmpty()) {
            new Thread(() -> {
                Geocoder geocoder = new Geocoder(MainActivity.this);
                try {
                    List<Address> addresses = geocoder.getFromLocationName(destination, 1);
                    if (addresses.size() > 0) {
                        runOnUiThread(() -> {
                            Address address = addresses.get(0);
                            LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());
                            mMap.addMarker(new MarkerOptions().position(latLng).title("Destination"));
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
                            // Implement route drawing if needed
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    // Handle exception, for example, show a toast message
                }
            }).start();
        }
    }

    private void drawRoute(SearchView location, LatLng latLng) {
        Toast.makeText(this, "aa", Toast.LENGTH_SHORT).show();
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
                                        LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                                        setAddressInEditText(userLocation);
                                        mMap.addMarker(new MarkerOptions().position(userLocation).title("Your Location"));
                                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 16));
                                    }
                                });
                    }
                } else {
                    // Coordinates for New Delhi, India
                    LatLng india = new LatLng(28.6139, 77.2090);
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(india, 5));
                }
            }
        }
    }
}
