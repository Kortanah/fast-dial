package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.ExecutionStepResult
import com.example.data.model.FlowNode
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class Converters {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @TypeConverter
    fun fromFlowNodeList(list: List<FlowNode>?): String {
        if (list == null) return "[]"
        val type = Types.newParameterizedType(List::class.java, FlowNode::class.java)
        val adapter = moshi.adapter<List<FlowNode>>(type)
        return adapter.toJson(list)
    }

    @TypeConverter
    fun toFlowNodeList(json: String?): List<FlowNode> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val type = Types.newParameterizedType(List::class.java, FlowNode::class.java)
            val adapter = moshi.adapter<List<FlowNode>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromStepResultList(list: List<ExecutionStepResult>?): String {
        if (list == null) return "[]"
        val type = Types.newParameterizedType(List::class.java, ExecutionStepResult::class.java)
        val adapter = moshi.adapter<List<ExecutionStepResult>>(type)
        return adapter.toJson(list)
    }

    @TypeConverter
    fun toStepResultList(json: String?): List<ExecutionStepResult> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val type = Types.newParameterizedType(List::class.java, ExecutionStepResult::class.java)
            val adapter = moshi.adapter<List<ExecutionStepResult>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
