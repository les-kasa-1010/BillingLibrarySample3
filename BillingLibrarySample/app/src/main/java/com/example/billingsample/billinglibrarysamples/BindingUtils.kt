@file:JvmName("BindingUtils")

package com.example.billingsample.billinglibrarysamples

import androidx.databinding.InverseMethod
import com.example.billingsample.viewmodel.MainViewModel

/**
 * Data Binding用の関数
 * ラジオボタンの選択とセットを双方向データバインディングするためのもの
 */

@InverseMethod("buttonIdToType")
fun errorTypeToButtonId(errorType: MainViewModel.ErrorType?): Int {
    var selectedButtonId = -1

    errorType?.run {
        selectedButtonId = when (this) {
            MainViewModel.ErrorType.SUCCESS -> R.id.success
            MainViewModel.ErrorType.NOT_CONSUMED -> R.id.notConsumed
            MainViewModel.ErrorType.NOT_COMMITTED -> R.id.notCommitted
        }
    }

    return selectedButtonId
}

fun buttonIdToType(selectedButtonId: Int): MainViewModel.ErrorType? {
    var type: MainViewModel.ErrorType? = null
    when (selectedButtonId) {
        R.id.success -> {
            type = MainViewModel.ErrorType.SUCCESS
        }
        R.id.notConsumed -> {
            type = MainViewModel.ErrorType.NOT_CONSUMED
        }
        R.id.notCommitted -> {
            type = MainViewModel.ErrorType.NOT_COMMITTED
        }
    }
    return type
}