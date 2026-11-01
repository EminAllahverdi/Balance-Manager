package com.eminallahverdi.balance.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icons a category or account can use, stored by key in the database so data never depends on code symbols.
 * Adding an icon = one line here; the category editor shows them all.
 */
object BtIcons {
    val catalog: Map<String, ImageVector> = linkedMapOf(
        "shopping_cart" to Icons.Rounded.ShoppingCart,
        "restaurant" to Icons.Rounded.Restaurant,
        "local_cafe" to Icons.Rounded.LocalCafe,
        "work" to Icons.Rounded.Work,
        "local_taxi" to Icons.Rounded.LocalTaxi,
        "directions_bus" to Icons.Rounded.DirectionsBus,
        "directions_car" to Icons.Rounded.DirectionsCar,
        "local_gas_station" to Icons.Rounded.LocalGasStation,
        "local_parking" to Icons.Rounded.LocalParking,
        "medical_services" to Icons.Rounded.MedicalServices,
        "local_pharmacy" to Icons.Rounded.LocalPharmacy,
        "content_cut" to Icons.Rounded.ContentCut,
        "spa" to Icons.Rounded.Spa,
        "bolt" to Icons.Rounded.Bolt,
        "subscriptions" to Icons.Rounded.Subscriptions,
        "smartphone" to Icons.Rounded.Smartphone,
        "apps" to Icons.Rounded.Apps,
        "school" to Icons.Rounded.School,
        "shopping_bag" to Icons.Rounded.ShoppingBag,
        "checkroom" to Icons.Rounded.Checkroom,
        "sports_esports" to Icons.Rounded.SportsEsports,
        "redeem" to Icons.Rounded.Redeem,
        "card_giftcard" to Icons.Rounded.CardGiftcard,
        "family_restroom" to Icons.Rounded.FamilyRestroom,
        "flight" to Icons.Rounded.Flight,
        "hotel" to Icons.Rounded.Hotel,
        "pets" to Icons.Rounded.Pets,
        "payments" to Icons.Rounded.Payments,
        "account_balance" to Icons.Rounded.AccountBalance,
        "account_balance_wallet" to Icons.Rounded.AccountBalanceWallet,
        "credit_card" to Icons.Rounded.CreditCard,
        "savings" to Icons.Rounded.Savings,
        "handshake" to Icons.Rounded.Handshake,
        "swap_horiz" to Icons.Rounded.SwapHoriz,
        "more_horiz" to Icons.Rounded.MoreHoriz,
    )

    fun of(key: String?): ImageVector = catalog[key] ?: Icons.Rounded.MoreHoriz
}
