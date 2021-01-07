package com.example.billingsample.view

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import com.example.billingsample.billinglibrarysamples.R
import com.example.billingsample.billinglibrarysamples.SampleApplication
import com.example.billingsample.billinglibrarysamples.databinding.ActivityMainBinding
import com.example.billingsample.model.SKU_ITEM_100
import com.example.billingsample.model.SKU_ITEM_10000
import com.example.billingsample.model.SKU_STATIC_TEST
import com.example.billingsample.viewmodel.MainViewModel
import com.example.billingsample.viewmodel.MainViewModelFactory
import kotlinx.android.synthetic.main.activity_main.*

/**
 * アプリのメイン画面(唯一の画面)
 */
class MainActivity : AppCompatActivity() {

    // ViewModelの生成
    // コンストラクタに引数が必要なため、MainViewModelFactoryを自前で定義しているが、
    // コード自体はテンプレート的に書けるものである
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory((application as SampleApplication).repository)
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // データバインディングの設定
        val binding: ActivityMainBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_main)

        // データバインディングとライフサイクル連携の設定
        binding.lifecycleOwner = this
        // データバインディングの元データの設定
        binding.viewmodel = viewModel

        // 初期化結果をobserve
        viewModel.isSetupDone.observe(this, Observer {
            textView.text = "Billing service setup result: $it"
        })

        // ログ表示エリア用の文字列observe
        viewModel.resultString.observe(this, Observer {
            textView.text = it
        })

        // static responseの購入フロー開始
        staticResponse.setOnClickListener {
            viewModel.purchase(this, SKU_STATIC_TEST)
        }

        // coin100 購入フロー開始
        buyCG100.setOnClickListener {
            viewModel.purchase(this, SKU_ITEM_100)
        }

        // coin10000 購入フロー開始
        buyCG10000.setOnClickListener {
            viewModel.purchase(this, SKU_ITEM_10000)
        }

        // 全履歴表示
        showAll.setOnClickListener {
            viewModel.getAllHistory()
        }

        // ストア&ローカル再送信レシートのみ表示
        showResendReceipt.setOnClickListener {
            viewModel.queryPurchaseData()
        }

        // 遅延レシートの全消費を実行
        resendAll.setOnClickListener {
            viewModel.resendAll()
        }

        // 全データ削除
        deleteButton.setOnClickListener {
            viewModel.deleteAllData()
            textView.text = "Cleared ALL receipt data!"
        }

        // Billing Service初期化開始
        viewModel.initBillingService(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // AIDL版で処理が必要
        if (viewModel.handlePurchaseResult(requestCode, resultCode, data)) {
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
