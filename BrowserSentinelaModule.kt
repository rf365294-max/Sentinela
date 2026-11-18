package com.sentinela.app

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.InputStream
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class LocalScanResult(
    val fileName: String,
    val sha256: String,
    val sizeBytes: Long,
    val verdict: String,
    val details: List<String>
)

@Composable
fun BrowserSentinelaScreen() {
    val context = LocalContext.current
    var address by remember { mutableStateOf("https://www.google.com") }
    var currentUrl by remember { mutableStateOf("") }
    var browserStatus by remember { mutableStateOf("Navegação protegida pronta") }
    var scanResult by remember { mutableStateOf<LocalScanResult?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var clockText by remember { mutableStateOf("") }
    var zoneText by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val screenScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            val now = ZonedDateTime.now()
            clockText = now.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy • HH:mm:ss", Locale("pt", "BR")))
            zoneText = "${now.zone.id} • UTC${now.offset}"
            delay(1000)
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scanning = true
            scanResult = null
            browserStatus = "Analisando arquivo selecionado…"
            // A leitura é feita em coroutine para não bloquear a interface.
            screenScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { scanUserSelectedFile(context, uri) }
                    scanResult = result
                    browserStatus = when (result.verdict) {
                        "SUSPEITO" -> "Análise encontrou indicadores suspeitos"
                        "ALERTA" -> "Arquivo executável ou pacote detectado; requer análise especializada"
                        else -> "Análise básica concluída; não foram encontrados indicadores conhecidos"
                    }
                } catch (error: Exception) {
                    browserStatus = "Não foi possível analisar: ${error.message ?: "erro de leitura"}"
                    scanResult = null
                } finally {
                    scanning = false
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Navy).verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(color = Panel, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Green)
                    Spacer(Modifier.width(8.dp))
                    Text("RELÓGIO LOCAL E FUSO HORÁRIO", color = Green, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(clockText.ifBlank { "Carregando data e hora…" }, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(zoneText, color = Cyan, fontSize = 11.sp)
                Text("Fonte: relógio e fuso configurados no dispositivo", color = White.copy(alpha = .55f), fontSize = 9.sp)
            }
        }

        Surface(color = Panel, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Green)
                    Spacer(Modifier.width(8.dp))
                    Text("DEFESA DE NAVEGAÇÃO", color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text("Safe Browsing do Android System WebView, HTTPS obrigatório e bloqueio de erros de certificado.", color = White.copy(alpha = .75f), fontSize = 11.sp)
                Text("A proteção depende da versão e das atualizações do WebView instalado no aparelho.", color = Cyan, fontSize = 10.sp)
            }
        }

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Endereço HTTPS ou pesquisa") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            trailingIcon = {
                IconButton(onClick = {
                    val target = normalizeHttpsAddress(address)
                    if (target != null) {
                        address = target
                        currentUrl = target
                        browserStatus = "Carregando página segura…"
                        webViewRef?.loadUrl(target)
                    } else browserStatus = "Endereço inválido: use HTTPS"
                }) { Icon(Icons.Default.Search, contentDescription = "Abrir", tint = Cyan) }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = White, unfocusedTextColor = White,
                focusedBorderColor = Green, unfocusedBorderColor = White.copy(alpha = .3f),
                focusedLabelColor = Green, unfocusedLabelColor = White.copy(alpha = .6f),
                cursorColor = Green
            )
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { webViewRef?.goBack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = White) }
            IconButton(onClick = { webViewRef?.goForward() }) { Icon(Icons.Default.ArrowForward, contentDescription = "Avançar", tint = White) }
            IconButton(onClick = { webViewRef?.reload() }) { Icon(Icons.Default.Refresh, contentDescription = "Atualizar", tint = White) }
            IconButton(onClick = {
                address = "https://www.google.com"
                currentUrl = address
                webViewRef?.loadUrl(address)
            }) { Icon(Icons.Default.Home, contentDescription = "Início", tint = White) }
        }

        Surface(color = Color(0xFF0B2232), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(360.dp)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        settings.domStorageEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.setSupportMultipleWindows(false)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val uri = request.url
                                if (uri.scheme.equals("https", ignoreCase = true)) {
                                    currentUrl = uri.toString()
                                    address = currentUrl
                                    return false
                                }
                                browserStatus = "Navegação bloqueada: somente HTTPS é permitido"
                                return true
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                currentUrl = url
                                address = url
                                browserStatus = "Verificando e carregando página…"
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                currentUrl = url
                                address = url
                                browserStatus = "Página carregada • HTTPS"
                            }

                            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                                handler.cancel()
                                browserStatus = "Conexão bloqueada: certificado SSL/TLS inválido"
                            }

                            @RequiresApi(Build.VERSION_CODES.O_MR1)
                            override fun onSafeBrowsingHit(
                                view: WebView,
                                request: WebResourceRequest,
                                threatType: Int,
                                callback: SafeBrowsingResponse
                            ) {
                                callback.backToSafety(true)
                                browserStatus = "Safe Browsing bloqueou uma página classificada como perigosa"
                            }
                        }
                        webViewRef = this
                        loadUrl("https://www.google.com")
                    }
                },
                update = { webViewRef = it }
            )
        }
        DisposableEffect(webViewRef) {
            val activeView = webViewRef
            onDispose {
                activeView?.stopLoading()
                activeView?.loadUrl("about:blank")
                activeView?.webViewClient = WebViewClient()
                activeView?.destroy()
                if (webViewRef === activeView) webViewRef = null
            }
        }
        Text(currentUrl.ifBlank { "Nenhuma página aberta" }, color = White.copy(alpha = .55f), fontSize = 9.sp, maxLines = 2)
        Text(browserStatus, color = Cyan, fontSize = 11.sp)
        TextButton(onClick = {
            webViewRef?.clearHistory()
            webViewRef?.clearCache(true)
            CookieManager.getInstance().removeAllCookies {
                CookieManager.getInstance().flush()
                browserStatus = "Cookies, cache e histórico local do navegador foram limpos"
            }
            WebStorage.getInstance().deleteAllData()
        }) {
            Text("Limpar dados de navegação", color = Cyan)
        }
        Text("JavaScript está desativado por padrão para reduzir riscos; algumas páginas podem exigir scripts para funcionar.", color = White.copy(alpha = .58f), fontSize = 9.sp)
        TextButton(onClick = { showPrivacy = true }) { Text("Privacidade e uso de dados", color = Cyan) }
        if (showPrivacy) {
            AlertDialog(
                onDismissRequest = { showPrivacy = false },
                confirmButton = {
                    TextButton(onClick = { showPrivacy = false }) { Text("Fechar") }
                },
                title = { Text("Política de privacidade — BROWSER SENTINELA") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Responsável: RF Programação. Contato: rf104403@gmail.com.\n\n" +
                            "O navegador acessa os endereços escolhidos pelo usuário; os sites visitados podem receber dados técnicos conforme suas próprias políticas. O WebView e o Safe Browsing são componentes do Android.\n\n" +
                            "A agenda só é acessada após autorização explícita quando o usuário abre o recurso de contatos. Arquivos selecionados para análise são processados localmente: o app calcula SHA-256 e procura indicadores heurísticos básicos, sem enviar o arquivo a um servidor nesta versão.\n\n" +
                            "Dados dos módulos financeiros, preferências e conversas demonstrativas permanecem localmente no dispositivo. Não há conta de usuário, publicidade, analytics próprio, sincronização em nuvem ou servidor de mensagens nesta versão. Data, hora e fuso vêm das configurações do aparelho.\n\n" +
                            "O usuário pode apagar os dados locais limpando os dados do aplicativo ou desinstalando-o. A navegação em sites de terceiros está sujeita às políticas desses sites. Para dúvidas, solicitações de privacidade ou suporte, contate rf104403@gmail.com. A política completa também deve estar publicada em URL HTTPS pública antes da distribuição."
                        )
                        TextButton(onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:rf104403@gmail.com?subject=Privacidade%20-%20BROWSER%20SENTINELA"))
                            runCatching { context.startActivity(intent) }
                        }) {
                            Text("Contatar RF Programação", color = Cyan)
                        }
                    }
                }
            )
        }

        Surface(color = Panel, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = Green)
                    Spacer(Modifier.width(8.dp))
                    Text("ANÁLISE EXTRA DE ARQUIVOS", color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text("Escolha um arquivo para calcular SHA-256 e procurar alguns indicadores conhecidos e padrões suspeitos. A análise é local e não envia o arquivo.", color = White.copy(alpha = .75f), fontSize = 11.sp)
                Button(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    enabled = !scanning,
                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Navy)
                ) {
                    Text(if (scanning) "Analisando…" else "Selecionar arquivo para analisar")
                }
                scanResult?.let { result ->
                    HorizontalDivider(color = White.copy(alpha = .15f))
                    Text("Resultado: ${result.verdict}", color = if (result.verdict == "LIMPO NA ANÁLISE BÁSICA") Green else Color(0xFFFFC66D), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Arquivo: ${result.fileName} • ${result.sizeBytes} bytes", color = White, fontSize = 10.sp)
                    Text("SHA-256: ${result.sha256}", color = Cyan, fontSize = 9.sp)
                    result.details.forEach { Text("• $it", color = White.copy(alpha = .78f), fontSize = 10.sp) }
                }
            }
        }

        Text(
            "Limite técnico: esta versão não examina automaticamente todos os aplicativos instalados, não possui uma base mundial de assinaturas e não substitui um antivírus profissional. “Limpo” significa apenas que esta análise básica não encontrou os indicadores implementados.",
            color = White.copy(alpha = .55f), fontSize = 9.sp
        )
    }
}

private fun normalizeHttpsAddress(raw: String): String? {
    val value = raw.trim()
    if (value.isBlank()) return null
    val candidate = when {
        value.startsWith("https://", true) -> value
        value.contains(" ") -> "https://www.google.com/search?q=" + Uri.encode(value)
        value.contains("://") -> return null
        else -> "https://$value"
    }
    val parsed = Uri.parse(candidate)
    return candidate.takeIf { parsed.scheme.equals("https", true) && !parsed.host.isNullOrBlank() }
}

private fun scanUserSelectedFile(context: Context, uri: Uri): LocalScanResult {
    val name = queryDisplayName(context, uri)
    val digest = MessageDigest.getInstance("SHA-256")
    val details = mutableListOf<String>()
    val buffer = ByteArray(8192)
    val sample = java.io.ByteArrayOutputStream()
    var total = 0L
    var truncated = false

    val input = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("Não foi possível abrir o arquivo.")
    input.use { stream ->
        while (true) {
            val count = stream.read(buffer)
            if (count <= 0) break
            total += count
            digest.update(buffer, 0, count)
            if (sample.size() < 2 * 1024 * 1024) {
                val allowed = minOf(count, 2 * 1024 * 1024 - sample.size())
                sample.write(buffer, 0, allowed)
            } else truncated = true
            if (total > 100L * 1024L * 1024L) throw IllegalArgumentException("Arquivo excede o limite de análise de 100 MB.")
        }
    }

    val bytes = sample.toByteArray()
    val text = bytes.toString(Charsets.ISO_8859_1).lowercase(Locale.ROOT)
    val sha = digest.digest().joinToString("") { "%02x".format(it) }

    if (text.contains("x5o!p%@ap[4\\pzx54(p^)7cc)7}\$eicar-standard-antivirus-test-file!\$h+h*")) {
        details.add("Padrão de teste EICAR detectado.")
    }
    if (text.contains("powershell") && (text.contains("-enc") || text.contains("encodedcommand"))) {
        details.add("Combinação de PowerShell com comando codificado encontrada.")
    }
    if (text.contains("frombase64string") || text.contains("eval(atob(")) {
        details.add("Padrão de execução/decodificação dinâmica encontrado.")
    }
    if (name.contains(".pdf.exe", true) || name.contains(".jpg.exe", true) || name.contains(".doc.exe", true)) {
        details.add("Nome com dupla extensão potencialmente enganosa.")
    }

    val startsMZ = bytes.size >= 2 && bytes[0] == 'M'.code.toByte() && bytes[1] == 'Z'.code.toByte()
    val startsElf = bytes.size >= 4 && bytes[0] == 0x7f.toByte() && bytes[1] == 'E'.code.toByte() && bytes[2] == 'L'.code.toByte() && bytes[3] == 'F'.code.toByte()
    val startsDex = bytes.size >= 4 && bytes.copyOfRange(0, 4).toString(Charsets.ISO_8859_1) == "dex\n"
    val startsZip = bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    if (startsMZ || startsElf || startsDex) details.add("Cabeçalho de executável ou bytecode detectado; isso não prova que seja malicioso.")
    if (startsZip && name.endsWith(".apk", true)) details.add("Pacote APK selecionado; esta versão não faz análise completa de seus componentes.")
    if (truncated) details.add("Indicadores textuais foram procurados somente nos primeiros 2 MB; o SHA-256 cobre o arquivo inteiro.")
    if (details.isEmpty()) details.add("Nenhum dos indicadores heurísticos implementados foi encontrado.")

    val verdict = when {
        details.any { it.contains("EICAR") || it.contains("dupla extensão") || it.contains("PowerShell") || it.contains("dinâmica") } -> "SUSPEITO"
        startsMZ || startsElf || startsDex || (startsZip && name.endsWith(".apk", true)) -> "ALERTA"
        else -> "LIMPO NA ANÁLISE BÁSICA"
    }
    return LocalScanResult(name, sha, total, verdict, details)
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    var name = uri.lastPathSegment ?: "arquivo selecionado"
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = cursor.getString(index) ?: name
        }
    }
    return name
}
