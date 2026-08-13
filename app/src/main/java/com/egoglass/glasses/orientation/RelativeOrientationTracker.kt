package com.egoglass.glasses.orientation

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

data class RelativeOrientation(
    val yawDegrees: Double,
    val pitchDegrees: Double,
    val rollDegrees: Double,
)

class RelativeOrientationTracker {
    private var reference: Quaternion? = null

    @Synchronized
    fun update(w: Double, x: Double, y: Double, z: Double): RelativeOrientation? {
        val current = Quaternion.normalized(w, x, y, z) ?: return null
        val origin = reference ?: current.also { reference = it }
        val relative = origin.conjugate() * current
        return relative.toHeadEulerDegrees()
    }

    @Synchronized
    fun reset() {
        reference = null
    }

    private data class Quaternion(
        val w: Double,
        val x: Double,
        val y: Double,
        val z: Double,
    ) {
        fun conjugate() = Quaternion(w, -x, -y, -z)

        operator fun times(other: Quaternion) = Quaternion(
            w = w * other.w - x * other.x - y * other.y - z * other.z,
            x = w * other.x + x * other.w + y * other.z - z * other.y,
            y = w * other.y - x * other.z + y * other.w + z * other.x,
            z = w * other.z + x * other.y - y * other.x + z * other.w,
        )

        fun toHeadEulerDegrees(): RelativeOrientation {
            // Head convention: +yaw right around Y, +pitch up around X, +roll right around Z.
            val r02 = 2.0 * (x * z + w * y)
            val r22 = 1.0 - 2.0 * (x * x + y * y)
            val r12 = 2.0 * (y * z - w * x)
            val r10 = 2.0 * (x * y + w * z)
            val r11 = 1.0 - 2.0 * (x * x + z * z)
            val yaw = atan2(r02, r22)
            val pitch = asin((-r12).coerceIn(-1.0, 1.0))
            val roll = atan2(r10, r11)
            return RelativeOrientation(
                yawDegrees = Math.toDegrees(yaw),
                pitchDegrees = Math.toDegrees(pitch),
                rollDegrees = Math.toDegrees(roll),
            )
        }

        companion object {
            fun normalized(w: Double, x: Double, y: Double, z: Double): Quaternion? {
                if (!listOf(w, x, y, z).all(Double::isFinite)) return null
                val magnitude = sqrt(w * w + x * x + y * y + z * z)
                if (magnitude < 1e-9) return null
                return Quaternion(w / magnitude, x / magnitude, y / magnitude, z / magnitude)
            }
        }
    }
}
