package com.example.followme;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.example.followme.databinding.ActivityMainBinding;
import com.example.followme.databinding.LoginAlertBinding;
import com.example.followme.databinding.RegisterAlertBinding;
import com.example.followme.databinding.TripIdBinding;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String BASE_URL = "http://christopherhield-001-site4.htempurl.com/api/";
    private static final String PREF_NAME = "UserCredentials";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_SAVE_CREDENTIALS = "saveCredentials";

    private ActivityMainBinding binding;
    private OkHttpClient client;
    private ActivityResultLauncher<String[]> requestPermissionLauncher;
    private SharedPreferences sharedPreferences;
    private AlertDialog currentDialog;

    // Create an executor service with a fixed thread pool
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    // Handler for posting back to the main thread
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final int LOCATION_REQUEST = 111;
    private static final int BACKGROUND_LOCATION_REQUEST = 222;
    private static final int NOTIFICATION_REQUEST = 333;
    private PointReciever pointReceiver;
    private Intent locationServiceIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize OkHttpClient with proper timeouts
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();

        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        binding.login.setOnClickListener(v -> showLoginDialog());
        binding.tvFollow.setOnClickListener(v -> showTripIdDialog(false));

        initializePermissionLauncher();

        boolean isFirstRun = sharedPreferences.getBoolean("isFirstRun", true);
        if (isFirstRun) {
            requestPermissions();
            sharedPreferences.edit().putBoolean("isFirstRun", false).apply();
        }
    }

    private void initializePermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                isGranted -> {
                    boolean allGranted = true;
                    for (Boolean granted : isGranted.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }

                    if (!allGranted) {
                        Toast.makeText(this, "All permissions are required for app functionality", Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        dismissCurrentDialog();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up executor service
        executorService.shutdown();
        dismissCurrentDialog();
        binding = null;
    }

    private void dismissCurrentDialog() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
            currentDialog = null;
        }
    }

    private void requestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }

        if (!permissionsToRequest.isEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
        }
    }

    private void showLoginDialog() {
        dismissCurrentDialog();

        if(!isNetworkAvailable(this)){
            new AlertDialog.Builder(this)
                    .setTitle("Follow Me - No Network")
                    .setIcon(R.mipmap.ic_launcher_foreground)
                    .setMessage("No Network connection - cannot login now")
                    .setPositiveButton("Ok",(dialog, which) -> {
                        finish();
                    })
                    .show();
        }


        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LoginAlertBinding dialogBinding = LoginAlertBinding.inflate(getLayoutInflater());
        builder.setView(dialogBinding.getRoot());

        boolean savedCredentials = sharedPreferences.getBoolean(KEY_SAVE_CREDENTIALS, false);
        if (savedCredentials) {
            String savedUsername = sharedPreferences.getString(KEY_USERNAME, "");
            String savedPassword = sharedPreferences.getString(KEY_PASSWORD, "");
            dialogBinding.etUsername.setText(savedUsername);
            dialogBinding.etPassword.setText(savedPassword);
            dialogBinding.cbSaveCredentials.setChecked(true);
        }

        AlertDialog dialog = builder.create();
        currentDialog = dialog;

        dialogBinding.tvLogin.setOnClickListener(v -> {
            String username = dialogBinding.etUsername.getText().toString();
            String password = dialogBinding.etPassword.getText().toString();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Username and password are required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (dialogBinding.cbSaveCredentials.isChecked()) {
                sharedPreferences.edit()
                        .putString(KEY_USERNAME, username)
                        .putString(KEY_PASSWORD, password)
                        .putBoolean(KEY_SAVE_CREDENTIALS, true)
                        .apply();
            } else {
                sharedPreferences.edit()
                        .remove(KEY_USERNAME)
                        .remove(KEY_PASSWORD)
                        .putBoolean(KEY_SAVE_CREDENTIALS, false)
                        .apply();
            }

            verifyUserCredentials(username, password, dialog);
        });

        dialogBinding.tvRegister.setOnClickListener(v -> {
            dialog.dismiss();
            currentDialog = null;
            showRegisterDialog();
        });

        dialogBinding.tvCancel.setOnClickListener(v -> {
            dialog.dismiss();
            currentDialog = null;
        });

        if (!isFinishing()) {
            dialog.show();
        }
    }

    private void showRegisterDialog() {
        dismissCurrentDialog();

        if(!isNetworkAvailable(this)){
            new AlertDialog.Builder(this)
                    .setTitle("Follow Me - No Network")
                    .setIcon(R.mipmap.ic_launcher_foreground)
                    .setMessage("No Network connection - cannot create user account now")
                    .setPositiveButton("Ok",(dialog, which) -> {
                        finish();
                    })
                    .show();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        RegisterAlertBinding dialogBinding = RegisterAlertBinding.inflate(getLayoutInflater());
        builder.setView(dialogBinding.getRoot());

        AlertDialog dialog = builder.create();
        currentDialog = dialog;

        dialogBinding.tvLogin.setOnClickListener(v -> {
            String firstName = dialogBinding.etUsername.getText().toString();
            String lastName = dialogBinding.firstName.getText().toString();
            String email = dialogBinding.editTextTextEmailAddress.getText().toString();
            String username = dialogBinding.user.getText().toString();
            String password = dialogBinding.etPassword.getText().toString();

            if (!validateRegistrationInput(firstName, lastName, email, username, password)) {
                return;
            }

            createUserAccount(firstName, lastName, email, username, password, dialog);
        });

        dialogBinding.tvCancel.setOnClickListener(v -> {
            dialog.dismiss();
            currentDialog = null;
        });

        if (!isFinishing()) {
            dialog.show();
        }
    }

    private boolean validateRegistrationInput(String firstName, String lastName, String email,
                                              String username, String password) {
        if (firstName.isEmpty() || firstName.length() > 100) {
            Toast.makeText(this, "First name must be between 1 and 100 characters", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (lastName.isEmpty() || lastName.length() > 100) {
            Toast.makeText(this, "Last name must be between 1 and 100 characters", Toast.LENGTH_SHORT).show();
            return false;
        }

        String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
        if (!Pattern.compile(emailRegex).matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (username.length() < 8 || username.length() > 12) {
            Toast.makeText(this, "Username must be between 8 and 12 characters", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (password.length() < 8 || password.length() > 12) {
            Toast.makeText(this, "Password must be between 8 and 12 characters", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private void verifyUserCredentials(String username, String password, AlertDialog dialog) {
        // Run network operation on background thread
        executorService.execute(() -> {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("userName", username);
                jsonObject.put("password", password);

                RequestBody requestBody = RequestBody.create(
                        MediaType.parse("application/json"),
                        jsonObject.toString()
                );

                Request request = new Request.Builder()
                        .url(BASE_URL + "UserAccounts/VerifyUserCredentials")
                        .put(requestBody)
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "Login failed: " + e.getMessage());
                        mainHandler.post(() -> {
                            Toast.makeText(MainActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (response.isSuccessful()) {
                            String responseData = response.body().string();
                            try {
                                JSONObject jsonResponse = new JSONObject(responseData);
                                boolean result = jsonResponse.getBoolean("result");

                                if (result) {
                                    String firstName = jsonResponse.getString("firstName");
                                    String lastName = jsonResponse.getString("lastName");

                                    mainHandler.post(() -> {
                                        if (dialog.isShowing()) {
                                            dialog.dismiss();
                                            currentDialog = null;
                                        }
                                        Toast.makeText(MainActivity.this, "Welcome " + firstName + " " + lastName, Toast.LENGTH_SHORT).show();
                                        showTripOptionsDialog();
                                    });
                                } else {
                                    mainHandler.post(() -> {
                                        Toast.makeText(MainActivity.this, "Invalid username or password", Toast.LENGTH_SHORT).show();
                                    });
                                }
                            } catch (JSONException e) {
                                Log.e(TAG, "JSON parsing error: " + e.getMessage());
                                mainHandler.post(() -> {
                                    Toast.makeText(MainActivity.this, "Error parsing response", Toast.LENGTH_SHORT).show();
                                });
                            }
                        } else if (response.code() == 401) {
                            mainHandler.post(() -> {
                                Toast.makeText(MainActivity.this, "Invalid username or password", Toast.LENGTH_SHORT).show();
                            });
                        } else {
                            mainHandler.post(() -> {
                                Toast.makeText(MainActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                });
            } catch (JSONException e) {
                Log.e(TAG, "JSON creation error: " + e.getMessage());
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "Error creating request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void createUserAccount(String firstName, String lastName, String email,
                                   String username, String password, AlertDialog dialog) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("firstName", firstName);
            jsonObject.put("lastName", lastName);
            jsonObject.put("userName", username);
            jsonObject.put("password", password);
            jsonObject.put("email", email);

            RequestBody requestBody = RequestBody.create(
                    MediaType.parse("application/json"),
                    jsonObject.toString()
            );

            Request request = new Request.Builder()
                    .url(BASE_URL + "UserAccounts/CreateUserAccount")
                    .post(requestBody)
                    .build();

            // OkHttp's enqueue already runs on a background thread, no need for executorService
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Account creation failed: " + e.getMessage());
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {

                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody == null) {
                            mainHandler.post(() -> {
                                Toast.makeText(MainActivity.this, "Empty response from server", Toast.LENGTH_SHORT).show();
                            });
                            return;
                        }

                        String responseData = responseBody.string();

                        if (response.isSuccessful()) {
                            mainHandler.post(() -> {
                                if (dialog != null && dialog.isShowing()) {
                                    dialog.dismiss();
                                    currentDialog = null;
                                }
                                Toast.makeText(MainActivity.this, "Account created successfully", Toast.LENGTH_SHORT).show();
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("Follow Me - Registration\nSuccessful")
                                                .setIcon(R.mipmap.ic_launcher_foreground)
                                                        .setMessage(" Welcome " + username + "!\n\n\n" + "Your username is:" + username+"\n" + "Your email is:" + email)
                                                                .setPositiveButton("Ok",(dialog, which) -> {
                                                                                    showLoginDialog();
                                                                                })
                                        .show();

                            });
                        } else if (response.code() == 400 || response.code() == 409) {
                            if (response.code() == 409) {
                                mainHandler.post(() -> {
                                    Toast.makeText(MainActivity.this, "Username already exists", Toast.LENGTH_LONG).show();
                                    if (dialog != null && dialog.isShowing()) {
                                        // Keep the current dialog open
                                        AlertDialog.Builder errorBuilder = new AlertDialog.Builder(MainActivity.this);
                                        errorBuilder.setTitle("Registration Failed");
                                        errorBuilder.setMessage("Username" + "'" + username + "'" + "already exists.");
                                        errorBuilder.setPositiveButton("Ok", (dialogInterface, which) -> {
                                            // Do nothing, which keeps the original registration dialog open
                                        });
                                        errorBuilder.setCancelable(false);
                                        errorBuilder.show();
                                    } else {
                                        showErrorDialog("Username already exists. Please choose a different username.", () -> showCreateTripDialog());
                                    }
                                });
                                return;
                            }
                            try {
                                JSONObject errorResponse = new JSONObject(responseData);
                                String errorMessage = errorResponse.optString("message", "User creation failed");

                                mainHandler.post(() -> {
                                    Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();

                                    // Add AlertDialog to show failure reason
                                    showErrorDialog(errorMessage, () -> {
                                        // Re-display the user creation dialog when "Ok" is pressed
                                        showCreateTripDialog();
                                    });
                                });
                            } catch (JSONException e) {
                                Log.e(TAG, "JSON parsing error: " + e.getMessage());
                                mainHandler.post(() -> {
                                    Toast.makeText(MainActivity.this, "Failed to create account", Toast.LENGTH_SHORT).show();
                                    showErrorDialog("Failed to create account", () -> showCreateTripDialog());
                                });
                            }
                        } else {
                            mainHandler.post(() -> {
                                String message = "Server error: " + response.code();
                                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                                showErrorDialog(message, () -> showCreateTripDialog());
                            });
                        }
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "JSON creation error: " + e.getMessage());
            mainHandler.post(() -> {
                Toast.makeText(MainActivity.this, "Error creating request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    // Helper method to show error dialog
    private void showErrorDialog(String message, Runnable onOkClicked) {
        mainHandler.post(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Account Creation Failed");
            builder.setMessage(message);
            builder.setPositiveButton("Ok", (dialog, which) -> {
                if (onOkClicked != null) {
                    onOkClicked.run();
                }
            });
            builder.setCancelable(false);

            AlertDialog dialog = builder.create();
            currentDialog = dialog;
            dialog.show();
        });
    }

    private void showTripOptionsDialog() {
        showCreateTripDialog();
    }

    private void showCreateTripDialog() {
        showTripIdDialog(true);
    }

    private void showTripIdDialog(boolean isLeadTrip) {
        dismissCurrentDialog();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        TripIdBinding dialogBinding = TripIdBinding.inflate(getLayoutInflater());
        builder.setView(dialogBinding.getRoot());

//        builder.setTitle(isLeadTrip ? "Create New Trip" : "Follow Existing Trip");

        final AlertDialog dialog = builder.create();
        currentDialog = dialog;

        if (!isLeadTrip) {
            dialogBinding.tvRegister.setVisibility(android.view.View.GONE);
        } else {
            dialogBinding.tvRegister.setOnClickListener(v -> {
                String generatedId = generateUniqueTripId();
                dialogBinding.etUsername.setText(generatedId);
            });
        }

        dialogBinding.tvLogin.setOnClickListener(v -> {
            String tripId = dialogBinding.etUsername.getText().toString().trim();
            if (tripId.isEmpty()) {
                Toast.makeText(MainActivity.this, "Trip ID cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isLeadTrip) {
                verifyTripIdUniqueness(tripId, dialog);
            } else {
                verifyTripExists(tripId, dialog);
            }
        });

        dialogBinding.tvCancel.setOnClickListener(v -> {
            dialog.dismiss();
            currentDialog = null;
        });

        if (!isFinishing()) {
            dialog.show();
        }
    }

    private String generateUniqueTripId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            int index = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(index));
        }

        sb.append('-');
        for (int i = 0; i < 4; i++) {
            int index = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    private void verifyTripIdUniqueness(String tripId, AlertDialog dialog) {
        // Run network operation on background thread
        executorService.execute(() -> {
            Request request = new Request.Builder()
                    .url(BASE_URL + "Datapoints/TripExists/" + tripId)
                    .get()
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Trip uniqueness check failed: " + e.getMessage());
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseData = response.body().string().trim();

                        mainHandler.post(() -> {
                            if (responseData.equalsIgnoreCase("false")) {
                                if (dialog.isShowing()) {
                                    dialog.dismiss();
                                    currentDialog = null;
                                }
                                navigateToTripLeadActivity(tripId);
                            } else {
                                Toast.makeText(MainActivity.this, "Trip ID already exists. Please choose another.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        mainHandler.post(() -> {
                            Toast.makeText(MainActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        });
    }

    private void verifyTripExists(String tripId, AlertDialog dialog) {
        // Run network operation on background thread
        executorService.execute(() -> {
            Request request = new Request.Builder()
                    .url(BASE_URL + "Datapoints/TripExists/" + tripId)
                    .get()
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Trip existence check failed: " + e.getMessage());
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseData = response.body().string().trim();

                        mainHandler.post(() -> {
                            if (responseData.equalsIgnoreCase("true")) {
                                if (dialog.isShowing()) {
                                    dialog.dismiss();
                                    currentDialog = null;
                                }
                                navigateToTripFollowActivity(tripId);
                            } else {
                                Toast.makeText(MainActivity.this, "Trip ID does not exist. Please enter a valid Trip ID.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        mainHandler.post(() -> {
                            Toast.makeText(MainActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        });
    }

    private void navigateToTripLeadActivity(String tripId) {
        Intent intent = new Intent(this, TripLeadActivity.class);
        intent.putExtra("TRIP_ID", tripId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void navigateToTripFollowActivity(String tripId) {
        Intent intent = new Intent(this, TripFollowerActivity.class);
        intent.putExtra("TRIP_ID", tripId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }
    public static boolean isNetworkAvailable(Context context) {
        if (context == null) return false;

        ConnectivityManager connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) return false;

        NetworkCapabilities capabilities = connectivityManager
                .getNetworkCapabilities(connectivityManager.getActiveNetwork());

        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    public static boolean isGpsEnabled(Context context) {
        LocationManager locationManager = (LocationManager)
                context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager != null &&
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }
}