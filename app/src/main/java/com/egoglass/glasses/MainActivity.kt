package com.egoglass.glasses

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.egoglass.glasses.domain.connection.SdkConnection
import com.egoglass.glasses.domain.connection.SdkConnectionListener
import com.egoglass.glasses.domain.connection.SdkConnectionState

class MainActivity : Activity() {
    private val connection: SdkConnection
        get() = (application as EgoGlassApplication).sdkConnection

    private lateinit var statusIndicator: View
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var retryButton: Button

    private val stateListener = SdkConnectionListener { state ->
        runOnUiThread { render(state) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusIndicator = findViewById(R.id.status_indicator)
        statusTitle = findViewById(R.id.status_title)
        statusDetail = findViewById(R.id.status_detail)
        retryButton = findViewById(R.id.retry_button)
        retryButton.setOnClickListener { connection.start() }
    }

    override fun onStart() {
        super.onStart()
        connection.addListener(stateListener)
        connection.start()
    }

    override fun onStop() {
        connection.removeListener(stateListener)
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) {
            connection.close()
        }
        super.onDestroy()
    }

    private fun render(state: SdkConnectionState) {
        val model = state.toUiModel()
        statusIndicator.backgroundTintList = ColorStateList.valueOf(getColor(model.color))
        statusTitle.setText(model.title)
        statusDetail.setText(model.detail)
        retryButton.visibility = if (state.canRetry) View.VISIBLE else View.INVISIBLE
        if (state.canRetry) {
            retryButton.requestFocus()
        }
    }

    private fun SdkConnectionState.toUiModel(): StatusUiModel = when (this) {
        SdkConnectionState.IDLE -> StatusUiModel(
            R.string.status_idle,
            R.string.status_idle_detail,
            R.color.status_inactive,
        )
        SdkConnectionState.CONNECTING -> StatusUiModel(
            R.string.status_connecting,
            R.string.status_connecting_detail,
            R.color.status_pending,
        )
        SdkConnectionState.REGISTERING -> StatusUiModel(
            R.string.status_registering,
            R.string.status_registering_detail,
            R.color.status_pending,
        )
        SdkConnectionState.READY -> StatusUiModel(
            R.string.status_ready,
            R.string.status_ready_detail,
            R.color.status_ready,
        )
        SdkConnectionState.DISCONNECTED -> StatusUiModel(
            R.string.status_disconnected,
            R.string.status_disconnected_detail,
            R.color.status_error,
        )
        SdkConnectionState.ERROR -> StatusUiModel(
            R.string.status_error,
            R.string.status_error_detail,
            R.color.status_error,
        )
    }

    private data class StatusUiModel(
        val title: Int,
        val detail: Int,
        val color: Int,
    )
}
