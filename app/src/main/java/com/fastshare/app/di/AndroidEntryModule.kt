package com.fastshare.app.di

import android.content.Context
import com.fastshare.app.services.transfer.InboundTransferServer
import com.fastshare.app.services.transfer.TransferStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideInboundServer(
        @ApplicationContext context: Context,
        identityManager: com.fastshare.app.services.security.IdentityManager,
        cryptoEngine: com.fastshare.app.services.security.CryptoEngine,
        tlsFactory: com.fastshare.app.services.security.TlsFactory,
        pairingManager: com.fastshare.app.services.security.PairingManager,
        storage: TransferStorage,
    ): InboundTransferServer =
        InboundTransferServer(context, identityManager, cryptoEngine, tlsFactory, pairingManager, storage)

    @Provides
    @Singleton
    fun provideTransferStorage(@ApplicationContext context: Context) =
        TransferStorage(context)

    @Provides
    @Singleton
    fun provideHttpClient(
        crypto: com.fastshare.app.services.security.CryptoEngine,
        @ApplicationContext context: Context,
    ) = com.fastshare.app.services.transfer.TransferHttpClient(crypto, context)

    @Provides
    @Singleton
    fun provideNsdEngine(@ApplicationContext context: Context) =
        com.fastshare.app.data.network.discovery.NsdDiscoveryEngine(context)

    @Provides
    @Singleton
    fun provideMulticastEngine(@ApplicationContext context: Context) =
        com.fastshare.app.data.network.discovery.MulticastDiscoveryEngine(context)

    @Provides
    @Singleton
    fun provideDiscoveryCoordinator(
        @ApplicationContext context: Context,
        nsd: com.fastshare.app.data.network.discovery.NsdDiscoveryEngine,
        multicast: com.fastshare.app.data.network.discovery.MulticastDiscoveryEngine,
        repo: com.fastshare.app.data.network.discovery.DeviceRepository,
        identity: com.fastshare.app.services.security.IdentityManager,
        settings: com.fastshare.app.data.local.datastore.SettingsRepository,
    ) = com.fastshare.app.services.discovery.DiscoveryCoordinator(
        context, nsd, multicast, repo, identity, settings,
    )
}
