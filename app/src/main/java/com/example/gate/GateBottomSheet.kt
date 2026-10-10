package com.example.gate

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.launch

class GateBottomSheet : BottomSheetDialogFragment() {

    private var flagId: String = ""
    private val currentFlagState = mutableStateOf<GateFlag?>(null)

    fun updateFlag(flag: GateFlag) {
        flagId = flag.flagId
        currentFlagState.value = flag
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Rule: Non-dismissible sheet
        isCancelable = false
        arguments?.let { args ->
            flagId = args.getString(ARG_FLAG_ID, "")
            val title = args.getString(ARG_TITLE, "")
            val message = args.getString(ARG_MESSAGE, "")
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                args.getParcelableArrayList(ARG_OPTIONS, GateOption::class.java) ?: arrayListOf()
            } else {
                @Suppress("DEPRECATION")
                args.getParcelableArrayList(ARG_OPTIONS) ?: arrayListOf()
            }
            val initialFlag = GateFlag(
                flagId = flagId,
                active = true,
                title = title,
                message = message,
                options = options
            )
            currentFlagState.value = initialFlag
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCanceledOnTouchOutside(false)
        
        // Intercept and consume back button / back gesture
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                true // Consumed - prevents dismissal
            } else {
                false
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.let { dialog ->
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.isHideable = false
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        GateManager.onBottomSheetDismissed()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    val activeFlag = currentFlagState.value
                    if (activeFlag != null) {
                        GateBottomSheetContent(
                            title = activeFlag.title,
                            message = activeFlag.message,
                            options = activeFlag.options,
                            onOptionSelected = { option, setLoadingState, onError ->
                                // Paused per user request
                                setLoadingState(false)
                                onError("Action is currently paused.")
                            }
                        )
                    }
                }
            }
        }
    }

    fun getFlagId(): String = flagId

    companion object {
        const val TAG = "GateBottomSheet"
        private const val ARG_FLAG_ID = "arg_flag_id"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_MESSAGE = "arg_message"
        private const val ARG_OPTIONS = "arg_options"

        fun newInstance(flag: GateFlag): GateBottomSheet {
            val fragment = GateBottomSheet()
            val args = Bundle().apply {
                putString(ARG_FLAG_ID, flag.flagId)
                putString(ARG_TITLE, flag.title)
                putString(ARG_MESSAGE, flag.message)
                putParcelableArrayList(ARG_OPTIONS, ArrayList(flag.options))
            }
            fragment.arguments = args
            return fragment
        }
    }
}

@Composable
private fun GateBottomSheetContent(
    title: String,
    message: String,
    options: List<GateOption>,
    onOptionSelected: (GateOption, (Boolean) -> Unit, (String) -> Unit) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var loadingOptionAction by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 36.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            options.forEach { option ->
                val isLoading = loadingOptionAction == option.action
                val isAnyLoading = loadingOptionAction != null

                Button(
                    onClick = {
                        if (!isAnyLoading) {
                            onOptionSelected(
                                option,
                                { loading ->
                                    loadingOptionAction = if (loading) option.action else null
                                },
                                { errorMessage ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(errorMessage)
                                    }
                                }
                            )
                        }
                    },
                    enabled = !isAnyLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.example.ui.theme.MtnYellow,
                        contentColor = Color.Black,
                        disabledContainerColor = com.example.ui.theme.MtnYellow.copy(alpha = 0.85f),
                        disabledContentColor = Color.Black
                    )
                ) {
                    if (isLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Please wait...",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = Color.Black
                            )
                        }
                    } else {
                        Text(
                            text = option.buttonText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SnackbarHost(hostState = snackbarHostState)
        }
    }
}
