package com.doomscroll.android.di

import android.content.Context
import com.doomscroll.android.core.util.AndroidPermissionChecker
import com.doomscroll.android.data.db.DoomscrollDatabase
import com.doomscroll.android.data.db.MetricsDao
import com.doomscroll.android.data.local.BreakDataStore
import com.doomscroll.android.data.local.PreferencesDataStore
import com.doomscroll.android.data.repository.MetricsRepository
import com.doomscroll.android.data.repository.MetricsRepositoryImpl
import com.doomscroll.android.data.repository.SettingsRepository
import com.doomscroll.android.data.repository.SettingsRepositoryImpl
import com.doomscroll.android.data.repository.UsageRepository
import com.doomscroll.android.data.repository.UsageRepositoryImpl
import com.doomscroll.android.domain.PermissionChecker
import com.doomscroll.android.domain.interceptor.InterceptionEngine
import com.doomscroll.android.domain.limiter.Enforcer
import com.doomscroll.android.domain.limiter.MindfulBreakManager
import com.doomscroll.android.notification.BreakNotifier
import com.doomscroll.android.service.UsageLimitEnforcer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/** Process-lifetime scope for app-wide work (break timers, notifications). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context) = PreferencesDataStore(context)

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DoomscrollDatabase = DoomscrollDatabase.create(context)

    @Provides
    fun provideMetricsDao(db: DoomscrollDatabase): MetricsDao = db.metricsDao()

    @Provides @Singleton
    fun provideSettingsRepository(prefs: PreferencesDataStore): SettingsRepository = SettingsRepositoryImpl(prefs)

    @Provides @Singleton
    fun provideUsageRepository(@ApplicationContext context: Context, dao: MetricsDao): UsageRepository =
        UsageRepositoryImpl(context, dao)

    @Provides @Singleton
    fun provideMetricsRepository(dao: MetricsDao, prefs: PreferencesDataStore, clock: Clock): MetricsRepository =
        MetricsRepositoryImpl(dao, prefs, clock)

    @Provides @Singleton
    fun provideInterceptionEngine(): InterceptionEngine = InterceptionEngine()

    @Provides @Singleton
    fun provideEnforcer(@ApplicationContext context: Context): Enforcer = UsageLimitEnforcer(context)

    @Provides @Singleton
    fun provideBreakManager(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock
    ): MindfulBreakManager = MindfulBreakManager(BreakDataStore(context), scope, clock)

    @Provides @Singleton
    fun provideBreakNotifier(@ApplicationContext context: Context, breakManager: MindfulBreakManager) =
        BreakNotifier(context, breakManager)

    @Provides @Singleton
    fun providePermissionChecker(@ApplicationContext context: Context): PermissionChecker = AndroidPermissionChecker(context)
}
