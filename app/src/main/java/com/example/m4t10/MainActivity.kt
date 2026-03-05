package com.example.m4t10

import android.Manifest
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.m4t10.ui.theme.M4t10Theme
import com.google.android.gms.location.LocationServices
import java.io.IOException
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            M4t10Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LocationScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun LocationScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var isLoading by remember { mutableStateOf(false) }
    var addressText by remember { mutableStateOf("Нажмите кнопку, чтобы определить адрес") }
    var coordinatesText by remember { mutableStateOf("—") }

    fun loadAddressFromLocation() {
        isLoading = true

        if (!hasLocationPermission(context)) {
            addressText = "Нет разрешения на геолокацию"
            coordinatesText = "—"
            isLoading = false
            return
        }

        if (!isLocationEnabled(context)) {
            addressText = "Службы геолокации отключены. Включите GPS/геолокацию."
            coordinatesText = "—"
            isLoading = false
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location == null) {
                    addressText = "Не удалось получить координаты. Попробуйте еще раз на открытом месте."
                    coordinatesText = "—"
                    isLoading = false
                    return@addOnSuccessListener
                }

                val lat = location.latitude
                val lng = location.longitude
                coordinatesText = String.format(Locale.US, "lat: %.5f / lng: %.5f", lat, lng)

                reverseGeocode(
                    context = context,
                    latitude = lat,
                    longitude = lng,
                    onSuccess = { resolvedAddress ->
                        addressText = resolvedAddress
                        isLoading = false
                    },
                    onError = { errorMessage ->
                        addressText = errorMessage
                        isLoading = false
                    }
                )
            }
            .addOnFailureListener {
                addressText = "Ошибка получения координат. Проверьте настройки GPS и разрешения."
                coordinatesText = "—"
                isLoading = false
            }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineGranted || coarseGranted) {
            loadAddressFromLocation()
        } else {
            addressText = "Доступ к геолокации отклонен пользователем"
            coordinatesText = "—"
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                if (hasLocationPermission(context)) {
                    loadAddressFromLocation()
                } else {
                    permissionsLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Получить мой адрес")
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = addressText,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = coordinatesText,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    return fineGranted || coarseGranted
}

private fun isLocationEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

private fun reverseGeocode(
    context: Context,
    latitude: Double,
    longitude: Double,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    if (!Geocoder.isPresent()) {
        onError("Геокодер недоступен на устройстве")
        return
    }

    val geocoder = Geocoder(context, Locale.getDefault())

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
            handleGeocoderResult(addresses, onSuccess, onError)
        }
    } else {
        try {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            handleGeocoderResult(addresses, onSuccess, onError)
        } catch (e: IOException) {
            onError("Ошибка сети при получении адреса. Проверьте интернет.")
        } catch (e: IllegalArgumentException) {
            onError("Некорректные координаты для геокодирования")
        }
    }
}

private fun handleGeocoderResult(
    addresses: List<Address>?,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val address = addresses?.firstOrNull()
    if (address == null) {
        onError("Адрес не найден. Возможно, нет интернета или данные недоступны.")
        return
    }

    val fullAddress = buildString {
        val line = address.getAddressLine(0)
        if (!line.isNullOrBlank()) {
            append(line)
        } else {
            listOfNotNull(
                address.thoroughfare,
                address.subLocality,
                address.locality,
                address.adminArea,
                address.countryName
            ).joinToString(", ").also { fallback ->
                if (fallback.isNotBlank()) append(fallback)
            }
        }
    }

    if (fullAddress.isBlank()) {
        onError("Не удалось сформировать читаемый адрес")
    } else {
        onSuccess(fullAddress)
    }
}

@Preview(showBackground = true)
@Composable
private fun LocationScreenPreview() {
    M4t10Theme {
        LocationScreen()
    }
}
