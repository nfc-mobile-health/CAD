/**
 * Dagger/Hilt module wiring the shared-core Fakes for CAD Track A UI.
 */
package com.smaple.cad.di

import com.smaple.core.fakes.FakeBackendApi
import com.smaple.core.fakes.FakeCredentialStore
import com.smaple.core.fakes.FakeSessionCoordinator
import com.smaple.core.session.BackendApi
import com.smaple.core.session.CredentialStore
import com.smaple.core.session.SessionCoordinator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindBackendApi(fakeBackendApi: FakeBackendApi): BackendApi

    @Binds
    @Singleton
    abstract fun bindSessionCoordinator(fakeSessionCoordinator: FakeSessionCoordinator): SessionCoordinator

    @Binds
    @Singleton
    abstract fun bindCredentialStore(fakeCredentialStore: FakeCredentialStore): CredentialStore
}
