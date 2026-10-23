package ir.andarz.app.market

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ir.andarz.market.MarketModule
import ir.andarz.market.MarketRepository

/**
 * The Hilt module that exposes MarketModule's parts to :app (K4). The repository is read lazily from the
 * installed module, so injecting it opens nothing; the hub starts only on `acquire()` with the gate open.
 */
@Module
@InstallIn(SingletonComponent::class)
object MarketDiModule {

    @Provides
    fun provideMarketRepository(): MarketRepository = MarketModule.repository
}
