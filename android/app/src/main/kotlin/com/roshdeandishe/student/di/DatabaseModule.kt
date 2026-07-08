package com.roshdeandishe.student.di

import android.content.Context
import androidx.room.Room
import com.roshdeandishe.student.data.local.AppDatabase
import com.roshdeandishe.student.data.local.MembershipDao
import com.roshdeandishe.student.data.local.ProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "roshdeandishe.db").build()

    @Provides
    fun provideProfileDao(database: AppDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideMembershipDao(database: AppDatabase): MembershipDao = database.membershipDao()
}
