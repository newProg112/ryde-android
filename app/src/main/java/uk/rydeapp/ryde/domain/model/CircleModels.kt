package uk.rydeapp.ryde.domain.model

data class CircleIdentity(
    val id: String,
    val name: String,
)

data class CircleMembership(
    val circle: HostedCircle,
    val isJoined: Boolean,
)

sealed interface JoinCircleResult {
    data class Joined(val membership: CircleMembership) : JoinCircleResult
    data class AlreadyJoined(val membership: CircleMembership) : JoinCircleResult
    data object CircleNotFound : JoinCircleResult
}

sealed interface LeaveCircleResult {
    data class Left(val membership: CircleMembership) : LeaveCircleResult
    data class NotJoined(val membership: CircleMembership?) : LeaveCircleResult
}
