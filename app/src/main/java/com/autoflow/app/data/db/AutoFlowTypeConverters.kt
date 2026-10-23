package com.autoflow.app.data.db

import androidx.room.TypeConverter
import com.autoflow.app.data.model.MatchStatus
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.SavingsType
import com.autoflow.app.data.model.TransactionSource

class AutoFlowTypeConverters {
    @TypeConverter
    fun fromRecordType(value: RecordType?): String? = value?.name

    @TypeConverter
    fun toRecordType(value: String?): RecordType? = value?.let { RecordType.valueOf(it) }

    @TypeConverter
    fun fromTransactionSource(value: TransactionSource?): String? = value?.name

    @TypeConverter
    fun toTransactionSource(value: String?): TransactionSource? = value?.let { TransactionSource.valueOf(it) }

    @TypeConverter
    fun fromReviewStatus(value: ReviewStatus?): String? = value?.name

    @TypeConverter
    fun toReviewStatus(value: String?): ReviewStatus? = value?.let { ReviewStatus.valueOf(it) }

    @TypeConverter
    fun fromMatchStatus(value: MatchStatus?): String? = value?.name

    @TypeConverter
    fun toMatchStatus(value: String?): MatchStatus? = value?.let { MatchStatus.valueOf(it) }

    @TypeConverter
    fun fromSavingsType(value: SavingsType?): String? = value?.name

    @TypeConverter
    fun toSavingsType(value: String?): SavingsType? = value?.let { SavingsType.valueOf(it) }
}
