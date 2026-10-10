package com.example.gate

import android.os.Parcel
import android.os.Parcelable

data class GateOption(
    val buttonText: String,
    val action: String
) : Parcelable {
    constructor(parcel: Parcel) : this(
        buttonText = parcel.readString() ?: "",
        action = parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(buttonText)
        parcel.writeString(action)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<GateOption> {
        override fun createFromParcel(parcel: Parcel): GateOption = GateOption(parcel)
        override fun newArray(size: Int): Array<GateOption?> = arrayOfNulls(size)
    }
}

data class GateFlag(
    val flagId: String,
    val active: Boolean,
    val title: String,
    val message: String,
    val options: List<GateOption>
) : Parcelable {
    constructor(parcel: Parcel) : this(
        flagId = parcel.readString() ?: "",
        active = parcel.readByte() != 0.toByte(),
        title = parcel.readString() ?: "",
        message = parcel.readString() ?: "",
        options = parcel.createTypedArrayList(GateOption.CREATOR) ?: emptyList()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(flagId)
        parcel.writeByte(if (active) 1 else 0)
        parcel.writeString(title)
        parcel.writeString(message)
        parcel.writeTypedList(options)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<GateFlag> {
        override fun createFromParcel(parcel: Parcel): GateFlag = GateFlag(parcel)
        override fun newArray(size: Int): Array<GateFlag?> = arrayOfNulls(size)
    }
}
