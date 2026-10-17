package com.alliehe.core.data.di

import com.alliehe.core.color.SchemeGenerator
import com.alliehe.core.color.nativeimpl.NativeSchemeGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    /** C++ generator (`libatrium_color.so`); falls back to the Kotlin implementation if the library can't load. */
    @Provides
    @Singleton
    fun nativeSchemeGenerator(): NativeSchemeGenerator = NativeSchemeGenerator()

    @Provides
    fun schemeGenerator(native: NativeSchemeGenerator): SchemeGenerator = native
}
