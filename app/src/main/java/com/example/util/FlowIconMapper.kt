package com.example.util

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.model.FlowCategory
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object FlowIconMapper {
    private const val TAG = "FlowIconMapper"

    private val iconCache = ConcurrentHashMap<String, ImageVector>()

    /**
     * Dynamically resolves ANY Material icon name from Firestore/DB.
     * Supports:
     * - snake_case (e.g. "arrow_forward", "account_balance_wallet", "shopping_cart", "send", "bolt")
     * - kebab-case (e.g. "arrow-forward")
     * - PascalCase (e.g. "ArrowForward")
     * - camelCase (e.g. "arrowForward")
     */
    fun getIcon(iconName: String?, fallbackCategory: FlowCategory? = null): ImageVector {
        if (iconName.isNullOrBlank()) {
            return getCategoryFallback(fallbackCategory)
        }

        val cleanName = iconName.trim()
        val cached = iconCache[cleanName]
        if (cached != null) {
            return cached
        }

        val resolved = resolveDynamicIcon(cleanName)
        if (resolved != null) {
            iconCache[cleanName] = resolved
            return resolved
        }

        return getCategoryFallback(fallbackCategory)
    }

    private fun resolveDynamicIcon(name: String): ImageVector? {
        val candidates = generatePascalCaseVariants(name)

        // Class names for receiver objects
        val receiverClassNames = listOf(
            "androidx.compose.material.icons.Icons\$Filled",
            "androidx.compose.material.icons.Icons\$Outlined",
            "androidx.compose.material.icons.Icons\$Rounded",
            "androidx.compose.material.icons.Icons\$Sharp",
            "androidx.compose.material.icons.Icons\$TwoTone",
            "androidx.compose.material.icons.Icons\$AutoMirrored\$Filled",
            "androidx.compose.material.icons.Icons\$AutoMirrored\$Outlined",
            "androidx.compose.material.icons.Icons\$AutoMirrored\$Rounded",
            "androidx.compose.material.icons.Icons\$AutoMirrored\$Sharp",
            "androidx.compose.material.icons.Icons\$AutoMirrored\$TwoTone"
        )

        // Icon package prefixes in material-icons-extended
        val iconPackages = listOf(
            "androidx.compose.material.icons.filled",
            "androidx.compose.material.icons.outlined",
            "androidx.compose.material.icons.rounded",
            "androidx.compose.material.icons.sharp",
            "androidx.compose.material.icons.twotone",
            "androidx.compose.material.icons.automirrored.filled",
            "androidx.compose.material.icons.automirrored.outlined",
            "androidx.compose.material.icons.automirrored.rounded",
            "androidx.compose.material.icons.automirrored.sharp",
            "androidx.compose.material.icons.automirrored.twotone"
        )

        val receivers = receiverClassNames.mapNotNull { className ->
            try {
                val clazz = Class.forName(className)
                val instance = try {
                    clazz.getField("INSTANCE").get(null)
                } catch (_: Exception) {
                    null
                }
                clazz to instance
            } catch (_: Exception) {
                null
            }
        }

        for (candidate in candidates) {
            val getterName = "get$candidate"

            // 1. Try standalone generated classes per icon, e.g. androidx.compose.material.icons.filled.ArrowForwardKt
            for (pkg in iconPackages) {
                val possibleClasses = listOf(
                    "$pkg.${candidate}Kt",
                    "$pkg._${candidate}Kt",
                    "$pkg.${candidate.replaceFirstChar { it.uppercase() }}Kt"
                )

                for (clsName in possibleClasses) {
                    try {
                        val clazz = Class.forName(clsName)
                        for ((receiverClass, instance) in receivers) {
                            try {
                                val method: Method = clazz.getMethod(getterName, receiverClass)
                                val result = method.invoke(null, instance) as? ImageVector
                                if (result != null) return result
                            } catch (_: NoSuchMethodException) {
                                // Continue
                            }
                        }

                        // Try finding any static method in the class returning ImageVector
                        for (m in clazz.methods) {
                            if (m.name.equals(getterName, ignoreCase = true) &&
                                ImageVector::class.java.isAssignableFrom(m.returnType)) {
                                val param = if (m.parameterCount == 1) {
                                    val pType = m.parameterTypes[0]
                                    receivers.firstOrNull { it.first == pType }?.second
                                } else null

                                val result = if (m.parameterCount == 1) {
                                    m.invoke(null, param) as? ImageVector
                                } else {
                                    m.invoke(null) as? ImageVector
                                }
                                if (result != null) return result
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore
                    }
                }
            }

            // 2. Try Core Icons directly on Icons.Filled / Icons.Outlined
            for ((receiverClass, instance) in receivers) {
                try {
                    val method: Method? = try {
                        receiverClass.getMethod(getterName)
                    } catch (_: NoSuchMethodException) {
                        null
                    }
                    if (method != null && ImageVector::class.java.isAssignableFrom(method.returnType)) {
                        val result = method.invoke(instance) as? ImageVector
                        if (result != null) return result
                    }
                } catch (_: Exception) {
                    // Ignore
                }
            }
        }

        return null
    }

    private fun generatePascalCaseVariants(raw: String): List<String> {
        val variants = mutableListOf<String>()
        variants.add(raw)

        val words = raw.split('_', '-', ' ', '.').filter { it.isNotEmpty() }
        if (words.isNotEmpty()) {
            val pascal = words.joinToString("") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            variants.add(pascal)

            val camel = words.first().lowercase() + words.drop(1).joinToString("") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            variants.add(camel)
        }

        return variants.distinct()
    }

    fun getCategoryFallback(category: FlowCategory?): ImageVector {
        return Icons.Filled.Widgets
    }
}
