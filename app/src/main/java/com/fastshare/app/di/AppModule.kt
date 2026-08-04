package com.fastshare.app.di

import android.content.Context
import androidx.room.Room
import com.fastshare.app.data.local.dao.TransferDao
import com.fastshare.app.data.local.dao.TransferEventDao
import com.fastshare.app.data.local.dao.TrustedDeviceDao
import com.fastshare.app.data.local.db.FastShareDatabase
import com.fastshare.app.services.security.CertificateProvider
import com.fastshare.app.services.security.CryptoEngine
import com.fastshare.app.services.security.IdentityManager
import com.fastshare.app.services.security.PairingManager
import com.fastshare.app.services.security.TlsFactory
import com.fastshare.app.services.security.TrustedFingerprintStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FastShareDatabase =
        Room.databaseBuilder(context, FastShareDatabase::class.java, FastShareDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTransferDao(db: FastShareDatabase): TransferDao = db.transferDao()

    @Provides
    fun provideTrustedDeviceDao(db: FastShareDatabase): TrustedDeviceDao = db.trustedDeviceDao()

    @Provides
    fun provideTransferEventDao(db: FastShareDatabase): TransferEventDao = db.transferEventDao()

    @Provides
    @Singleton
    fun provideIdentityManager(@ApplicationContext context: Context, certificateProvider: CertificateProvider): IdentityManager =
        IdentityManager(context, certificateProvider)

    @Provides
    @Singleton
    fun provideCryptoEngine(): CryptoEngine = CryptoEngine()
}

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    @Provides
    @Singleton
    fun provideCertificateProvider(@ApplicationContext context: Context): CertificateProvider =
        CertificateProvider(context)

    @Provides
    @Singleton
    fun provideTrustedFingerprintStore(dao: TrustedDeviceDao): TrustedFingerprintStore =
        TrustedFingerprintStore(dao)

    @Provides
    @Singleton
    fun provideTlsFactory(certificateProvider: CertificateProvider, trustStore: TrustedFingerprintStore): TlsFactory =
        TlsFactory(certificateProvider, trustStore)

    @Provides
    @Singleton
    fun providePairingManager(dao: TrustedDeviceDao, store: TrustedFingerprintStore): PairingManager =
        PairingManager(dao, store)
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideTransferRepository(
        transferDao: TransferDao,
        trustedDeviceDao: TrustedDeviceDao,
    ): com.fastshare.app.data.repository.TransferRepository =
        com.fastshare.app.data.repository.TransferRepository(transferDao, trustedDeviceDao)

    @Provides
    @Singleton
    fun provideDeviceRepository(
        dao: TrustedDeviceDao,
    ): com.fastshare.app.data.network.discovery.DeviceRepository =
        com.fastshare.app.data.network.discovery.DeviceRepository(dao)
}
