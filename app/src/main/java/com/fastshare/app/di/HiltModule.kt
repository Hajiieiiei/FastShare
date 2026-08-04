package com.fastshare.app.di

import com.fastshare.app.data.network.discovery.DeviceRepository
import com.fastshare.app.services.transfer.TransferEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TransferModule {
    @Provides
    @Singleton
    fun provideTransferEngine(
        context: android.content.Context,
        identityManager: com.fastshare.app.services.security.IdentityManager,
        cryptoEngine: com.fastshare.app.services.security.CryptoEngine,
        trustedDeviceDao: com.fastshare.app.data.local.dao.TrustedDeviceDao,
        httpClient: com.fastshare.app.services.transfer.TransferHttpClient,
        inboundServer: com.fastshare.app.services.transfer.InboundTransferServer,
    ): TransferEngine = TransferEngine(context, identityManager, cryptoEngine, trustedDeviceDao, httpClient, inboundServer)
}
