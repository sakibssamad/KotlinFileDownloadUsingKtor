package com.example.kotlin_file_download


import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.*
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.kotlin_file_download.databinding.FragmentMainBinding
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.*
import io.ktor.client.response.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.content.ContentResolver
import android.util.Log
import com.example.kotlin_file_download.retrofit.RetrofitClient
import okhttp3.Headers
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.R.string
import okhttp3.ResponseBody


class MainFragment : Fragment() {
    private lateinit var binding: FragmentMainBinding

    private lateinit var viewModel: MainViewModel

    private val PERMISSIONS = listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private val PERMISSION_REQUEST_CODE = 1
    private val DOWNLOAD_FILE_CODE = 2

    private val fileUrl = "http://10.11.201.180:8080/AgentBanking/NoticeDownloadS?id=134"//"https://css4.pub/2017/newsletter/drylab.pdf"//

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_main,
            container,
            false
        )

        binding.lifecycleOwner = viewLifecycleOwner

        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        if (hasPermissions(context, PERMISSIONS)) {
            setDownloadButtonClickListener()
        } else {
            requestPermissions(PERMISSIONS.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private  fun setDownloadButtonClickListener() {


        val call: Call<ResponseBody>? = RetrofitClient
            .instance
            ?.aPI
            ?.getFileType("134")
        call?.enqueue(object: Callback<ResponseBody> {

            override fun onResponse(call: Call<ResponseBody?>, response: Response<ResponseBody?>) {
                // get headers
                val headers: Headers = response.headers()
                // get header value
                val fileName: String = headers["File-Name"].toString()

                Log.d("File-Name", "onResponse: $fileName ")
                val folder = context?.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

                val file = File(folder, fileName)
                val uri = context?.let {
                    FileProvider.getUriForFile(it, "com.example.kotlin_file_download", file)
                }
                val extension = MimeTypeMap.getFileExtensionFromUrl(uri?.path)
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

                binding.viewButton.setOnClickListener {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    intent.setDataAndType(uri, mimeType)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    intent.putExtra(Intent.EXTRA_TITLE, fileName)
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    startActivityForResult(intent, DOWNLOAD_FILE_CODE)
                }
            }

            override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
                Log.d("TAG", "onResponseError: $t ")

            }

            })



    }

    private fun downloadFile(context: Context, url: String, file: Uri) {
        val ktor = HttpClient(Android)

        viewModel.setDownloading(true)
        context.contentResolver.openOutputStream(file)?.let { outputStream ->
            CoroutineScope(Dispatchers.IO).launch {
                ktor.downloadFile(outputStream, url).collect {
                    withContext(Dispatchers.Main) {
                        when (it) {
                            is DownloadResult.Success -> {
                                viewModel.setDownloading(false)
                                binding.progressBar.progress = 0
                                viewFile(file)
                            }

                            is DownloadResult.Error -> {
                                viewModel.setDownloading(false)
                                Toast.makeText(
                                    context,
                                    "Error while downloading file",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            is DownloadResult.Progress -> {
                                binding.progressBar.progress = it.progress
                            }
                        }
                    }
                }
            }
        }
    }

    private fun viewFile(uri: Uri) {
        context?.let { context ->
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(intent, "Open with")

            if (intent.resolveActivity(context.packageManager) != null) {
                startActivity(chooser)
            } else {
                Toast.makeText(context, "No suitable application to open file", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun hasPermissions(context: Context?, permissions: List<String>): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null) {
            return permissions.all { permission ->
                ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        }

        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE && hasPermissions(context, PERMISSIONS)) {
            setDownloadButtonClickListener()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == DOWNLOAD_FILE_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                context?.let { context ->
                    downloadFile(context, fileUrl, uri)
                }
            }
        }
    }
}
