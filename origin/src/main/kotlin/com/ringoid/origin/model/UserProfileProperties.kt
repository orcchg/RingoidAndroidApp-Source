package com.ringoid.origin.model

import com.ringoid.domain.DomainUtil
import com.ringoid.domain.misc.UserProfilePropertiesRaw
import com.ringoid.domain.model.Mappable

data class UserProfileProperties(
    var education: EducationProfileProperty = EducationProfileProperty.Unknown,
    var hairColor: HairColorProfileProperty = HairColorProfileProperty.Unknown,
    var height: Int = DomainUtil.UNKNOWN_VALUE,
    var income: IncomeProfileProperty = IncomeProfileProperty.Unknown,
    var property: PropertyProfileProperty = PropertyProfileProperty.Unknown,
    var transport: TransportProfileProperty = TransportProfileProperty.Unknown)
    : Mappable<UserProfilePropertiesRaw> {

    companion object {
        fun from(raw: UserProfilePropertiesRaw): UserProfileProperties =
            UserProfileProperties(
                education = EducationProfileProperty.from(raw.education),
                hairColor = HairColorProfileProperty.from(raw.hairColor),
                height = raw.height,
                income = IncomeProfileProperty.from(raw.income),
                property = PropertyProfileProperty.from(raw.property),
                transport = TransportProfileProperty.from(raw.transport))
    }

    override fun map(): UserProfilePropertiesRaw =
        UserProfilePropertiesRaw(
            education = education.id,
            hairColor = hairColor.id,
            height = height,
            income = income.id,
            property = property.id,
            transport = transport.id)
}
