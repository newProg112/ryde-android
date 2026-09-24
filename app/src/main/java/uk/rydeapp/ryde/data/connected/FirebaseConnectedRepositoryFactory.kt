package uk.rydeapp.ryde.data.connected

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import uk.rydeapp.ryde.BuildConfig

object FirebaseConnectedRepositoryFactory {
    private const val EMULATOR_HOST = "10.0.2.2"
    private const val AUTH_PORT = 9099
    private const val FIRESTORE_PORT = 8080

    val repository: ConnectedRydeRepository by lazy {
        check(BuildConfig.DEBUG) { "Firebase connected mode is emulator-only and unavailable in release builds" }
        val auth = FirebaseAuth.getInstance().apply {
            useEmulator(EMULATOR_HOST, AUTH_PORT)
        }
        val firestore = FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
        }
        ConnectedRydeRepository(
            auth = FirebaseAuthGateway(auth),
            profiles = FirestoreConnectedProfileStore(firestore),
            journeys = FirestoreConnectedJourneyStore(firestore),
            coordination = FirestoreConnectedCoordinationStore(firestore),
            placeResolver = DevelopmentFixturePlaceResolver(),
        )
    }
}
