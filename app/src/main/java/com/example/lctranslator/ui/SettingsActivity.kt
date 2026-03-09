package com.example.lctranslator.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.lctranslator.LlmClient
import com.example.lctranslator.LlmProvider
import com.example.lctranslator.Prefs
import com.example.lctranslator.R
import com.example.lctranslator.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private var fetchedModels: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val providers = LlmProvider.entries.map { it.name }
        binding.spinnerProvider.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, providers)
        binding.spinnerProvider.setSelection(providers.indexOf(Prefs.provider(this).name))

        val langNames = resources.getStringArray(R.array.language_names).toList()
        val langCodes = resources.getStringArray(R.array.language_codes).toList()
        binding.spinnerTargetLang.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, langNames)
        val langIdx = langCodes.indexOf(Prefs.targetLang(this)).takeIf { it >= 0 } ?: 0
        binding.spinnerTargetLang.setSelection(langIdx)

        binding.etApiKey.setText(Prefs.apiKey(this))
        binding.etBaseUrl.setText(Prefs.baseUrl(this))
        binding.etCaptionPkg.setText(Prefs.captionPkg(this))

        val savedModel = Prefs.model(this)
        if (savedModel.isNotBlank()) {
            fetchedModels = listOf(savedModel)
            populateModelSpinner(fetchedModels, savedModel)
        }

        binding.btnFetchModels.setOnClickListener { fetchModels() }
        binding.etBaseUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && binding.etBaseUrl.text.toString().isNotBlank()) fetchModels()
        }
        binding.btnSave.setOnClickListener { save(langCodes) }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun fetchModels() {
        val apiKey  = binding.etApiKey.text.toString().trim()
        val baseUrl = binding.etBaseUrl.text.toString().trim()
        val provider = LlmProvider.entries[binding.spinnerProvider.selectedItemPosition]
        binding.btnFetchModels.isEnabled = false
        binding.btnFetchModels.text = getString(R.string.fetching_models)
        lifecycleScope.launch {
            val models = runCatching {
                LlmClient.fetchModels(provider, apiKey, baseUrl)
            }.getOrElse {
                Toast.makeText(this@SettingsActivity,
                    "${getString(R.string.models_fetch_error)}: ${it.message}",
                    Toast.LENGTH_LONG).show()
                emptyList()
            }
            binding.btnFetchModels.isEnabled = true
            binding.btnFetchModels.text = getString(R.string.fetch_models)
            if (models.isEmpty()) {
                Toast.makeText(this@SettingsActivity,
                    getString(R.string.no_models_found), Toast.LENGTH_SHORT).show()
                return@launch
            }
            fetchedModels = models
            populateModelSpinner(models, Prefs.model(this@SettingsActivity))
        }
    }

    private fun populateModelSpinner(models: List<String>, selectModel: String) {
        binding.tvModelLabel.visibility = View.VISIBLE
        binding.spinnerModel.visibility = View.VISIBLE
        binding.spinnerModel.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, models)
        binding.spinnerModel.setSelection(models.indexOf(selectModel).takeIf { it >= 0 } ?: 0)
    }

    private fun save(langCodes: List<String>) {
        val apiKey     = binding.etApiKey.text.toString().trim()
        val baseUrl    = binding.etBaseUrl.text.toString().trim()
        val captionPkg = binding.etCaptionPkg.text.toString().trim()
            .ifBlank { getString(R.string.default_caption_pkg) }
        val targetLang = langCodes[binding.spinnerTargetLang.selectedItemPosition]
        val provider   = LlmProvider.entries[binding.spinnerProvider.selectedItemPosition].name
        val model      = if (fetchedModels.isNotEmpty())
            fetchedModels[binding.spinnerModel.selectedItemPosition]
        else Prefs.model(this)

        Prefs.get(this).edit()
            .putString(getString(R.string.pref_api_key),     apiKey)
            .putString(getString(R.string.pref_base_url),    baseUrl)
            .putString(getString(R.string.pref_model),       model)
            .putString(getString(R.string.pref_target_lang), targetLang)
            .putString(getString(R.string.pref_provider),    provider)
            .putString(getString(R.string.pref_caption_pkg), captionPkg)
            .apply()

        Toast.makeText(this, "Saved ✓", Toast.LENGTH_SHORT).show()
        finish()
    }
}
