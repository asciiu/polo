package utils

/**
  * Created by bishop on 9/5/16.
  */
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

import services.ExponentialMovingAverageActor

class CustomModule extends AbstractModule with AkkaGuiceSupport {
  def configure = {
    bindActor[ExponentialMovingAverageActor]("ema-actor")
  }
}
