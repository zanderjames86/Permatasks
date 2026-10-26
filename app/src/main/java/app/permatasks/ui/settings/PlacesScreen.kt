package app.permatasks.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.location.Address
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.permatasks.container
import app.permatasks.data.PlaceEntity
import app.permatasks.ui.components.EmptyState
import app.permatasks.ui.components.ListRow
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val places by remember { c.repo.places.observeAll() }.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<PlaceEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Places") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Add place") } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            if (places.isEmpty()) item { EmptyState(Icons.Outlined.Place, "No places yet", "Save places like Home, Office or the supermarket to get reminders when you arrive or leave.") }
            items(places, key = { it.id }) { p ->
                ListRow(p.name, p.address.ifBlank { "${p.radiusMeters} m radius" }, Icons.Outlined.Place, onClick = { editing = p }, showChevron = true)
            }
        }
    }
    if (adding) PlaceEditorDialog(null, { adding = false }) { adding = false }
    editing?.let { p ->
        PlaceEditorDialog(p, { editing = null }, onDelete = {
            scope.launch { c.repo.places.delete(p); c.repo.syncGeofences() }
            editing = null
        }) { editing = null }
    }
}

/** Create or edit a place by address search (on-device Geocoder) or current location. */
@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("MissingPermission")
@Composable
fun PlaceEditorDialog(existing: PlaceEntity?, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onSaved: (PlaceEntity) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Address>>(emptyList()) }
    var lat by remember { mutableStateOf(existing?.latitude) }
    var lng by remember { mutableStateOf(existing?.longitude) }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var radius by remember { mutableIntStateOf(existing?.radiusMeters ?: 150) }
    var status by remember { mutableStateOf<String?>(null) }

    fun useCurrentLocation() {
        scope.launch {
            status = "Finding you…"
            val loc = runCatching {
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
            }.getOrNull()
            if (loc == null) {
                status = "Couldn't get your location"
            } else {
                lat = loc.latitude; lng = loc.longitude; address = "Current location"; status = null
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) useCurrentLocation()
    }

    fun search() {
        if (query.isBlank()) return
        status = "Searching…"
        Geocoder(context).getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) { results = addresses; status = if (addresses.isEmpty()) "No results" else null }
            override fun onError(errorMessage: String?) { status = errorMessage ?: "Search failed" }
        })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New place" else "Edit place") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    query, { query = it }, label = { Text("Search address") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { IconButton(onClick = { search() }) { Icon(Icons.Outlined.Search, "Search") } },
                )
                results.take(4).forEach { a ->
                    val line = (0..a.maxAddressLineIndex).joinToString(", ") { a.getAddressLine(it) }
                    TextButton(onClick = {
                        lat = a.latitude; lng = a.longitude; address = line; results = emptyList()
                        if (name.isBlank()) name = a.featureName ?: line.substringBefore(',')
                    }) { Text(line, maxLines = 2) }
                }
                OutlinedButton(onClick = {
                    permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) {
                    Icon(Icons.Outlined.MyLocation, null); Text("  Use my current location")
                }
                if (lat != null) Text("📍 ${address.ifBlank { "%.5f, %.5f".format(lat, lng) }}", style = MaterialTheme.typography.bodySmall)
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                Text("Radius", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(100, 150, 300, 500, 1000).forEach { r -> FilterChip(radius == r, { radius = r }, label = { Text(if (r < 1000) "$r m" else "1 km") }) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && lat != null, onClick = {
                val p = (existing ?: PlaceEntity(name = name, latitude = lat!!, longitude = lng!!))
                    .copy(name = name.trim(), latitude = lat!!, longitude = lng!!, radiusMeters = radius, address = address)
                scope.launch { c.repo.places.upsert(p); c.repo.syncGeofences(); onSaved(p) }
            }) { Text("Save") }
        },
        dismissButton = {
            if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
