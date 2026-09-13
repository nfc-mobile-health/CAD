/**
 * Dagger Hilt dependency injection module for CAD.
 * Binds real implementations of shared-core interfaces.
 */
package com.smaple.cad.di

import android.content.Context
import com.smaple.cad.nfc.NfcReaderTransport
import com.smaple.core.protocol.ProtocolEngine
import com.smaple.core.protocol.SecureXferProtocolEngine
import com.smaple.core.session.BackendApi
import com.smaple.core.session.BackendApiImpl
import com.smaple.core.session.CredentialStore
import com.smaple.core.session.CredentialStoreImpl
import com.smaple.core.session.SessionCoordinator
import com.smaple.core.session.SessionCoordinatorImpl
import com.smaple.core.transport.Transport
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindBackendApi(impl: BackendApiImpl): BackendApi

    @Binds
    @Singleton
    abstract fun bindCredentialStore(impl: CredentialStoreImpl): CredentialStore

    @Binds
    @Singleton
    abstract fun bindSessionCoordinator(impl: SessionCoordinatorImpl): SessionCoordinator
    
    @Binds
    @Singleton
    abstract fun bindProtocolEngine(impl: SecureXferProtocolEngine): ProtocolEngine

    @Binds
    @Singleton
    abstract fun bindTransport(impl: NfcReaderTransport): Transport
}

@Module
@InstallIn(SingletonComponent::class)
object Providers {
    @Provides
    @Named("storageDir")
    fun provideStorageDir(@ApplicationContext context: Context): File {
        return context.filesDir
    }
}
