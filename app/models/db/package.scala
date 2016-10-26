package models

package object db {

  object AccountRole extends Enumeration {
    val normal, admin = Value
  }

  object OrderType extends Enumeration {
    val buy, sell = Value
  }
}
