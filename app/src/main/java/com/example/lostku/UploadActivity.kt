package com.example.lostku

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.BoringLayout
import android.util.Log
import android.view.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import com.example.lostku.databinding.ActivityUploadBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.UploadTask
import com.google.firebase.storage.ktx.storage
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*

class UploadActivity : AppCompatActivity() {
    lateinit var binding : ActivityUploadBinding
    lateinit var dlg : UploadDialog

    var isInfoSubmitted : Boolean = false
    var isImgSubmitted : Boolean = false
    var password : String? = ""

    val permission = android.Manifest.permission.READ_EXTERNAL_STORAGE

    val GALLERY_REQUEST_CODE = 1001

    var imageURI : Uri? = null;

    val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){
        if(!it){
            finish()
        }
    }


    val selectImageContract = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            binding.imageView.visibility = View.VISIBLE
            binding.imageView.setImageURI(uri)
            imageURI = uri
            isImgSubmitted = true
            setGalleyText(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUploadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        password = intent.getStringExtra("PASSWORD")
        initLayout()
    }

    fun setStateText(isSetNow : Boolean = false){
        if(isSetNow)
            binding.stateText.text = "분실물 정보 등록 준비 완료!"
        else
            binding.stateText.text = "아직 분실물 등록 전입니다!"
    }

    fun setGalleyText(isSetNow : Boolean = false){
        if(isSetNow)
            binding.pictureText.text = "분실물 이미지 등록 완료!"
        else
            binding.pictureText.text = "아직 사진 등록 전입니다!"
    }

    private fun submitInfo()
    {
        dlg = UploadDialog(this)
        dlg.show(this)
    }

    fun galleryAlertDlg(){
        val builder = AlertDialog.Builder(this)
        builder.setMessage("반드시 갤러리 접근 권한이 모두 허용되어야 합니다.")
            .setTitle("권한체크")
            .setPositiveButton("Ok"){
                    _, _ // 여기서 넘어오는 인자 2개는 안 쓸 것임.
                -> permissionLauncher.launch(permission)
            }
            .setNegativeButton("Cancel"){
                    dlg, _ -> dlg.dismiss()
            }

        val dlg = builder.create()
        dlg.show()
    }

    private fun imageAction(){
        when{
            ActivityCompat.checkSelfPermission(this,
                permission) == PackageManager.PERMISSION_GRANTED ->{
                submitImg()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this,
                permission) ->{
                // 명시적으로 거부했을 때
                galleryAlertDlg()
            }
            else -> {
                // 맨 처음에 실행했을 때
                permissionLauncher.launch(permission)
            }
        }
    }

    // 프로그레스바 보이기
    private fun showProgressBar() {
        val pBar = findViewById<ProgressBar>(R.id.progressBar)
        blockLayoutTouch()
        pBar.isVisible = true
    }

    // 프로그레스바 숨기기
    private fun hideProgressBar() {
        val pBar = findViewById<ProgressBar>(R.id.progressBar)
        clearBlockLayoutTouch()
        pBar.isVisible = false
    }

    // 화면 터치 막기
    private fun blockLayoutTouch() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    // 화면 터치 풀기
    private fun clearBlockLayoutTouch() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    private fun submitImg() {
//        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
//        startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE)

        selectImageContract.launch("image/*")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val selectedImage: Uri? = data.data
            // 선택한 이미지에 대한 처리를 수행합니다.
            binding.imageView.visibility = View.VISIBLE
            binding.imageView.setImageURI(selectedImage)
            imageURI = selectedImage
            isImgSubmitted = true
            setGalleyText(true)
        }
    }

    fun getImageUri() : String{
        return imageURI.toString()
    }

    private fun initLayout() {
        isInfoSubmitted = false
        isImgSubmitted = false

        hideProgressBar()

        // "아직 분실물 등록 전입니다!"
        setStateText()

        // "아직 사진 등록 전입니다!"
        setGalleyText()

        binding.apply {

            // 2023.05.28 사진 등록 부분은 맨 처음엔 안 보이도록. (사진 등록 후 노출)
            imageView.visibility = View.GONE

            submitInfoButton.setOnClickListener {
                // 분실물 정보 등록 버튼
                submitInfo()
            }
            submitImgButton.setOnClickListener {
                // 이미지 제출 버튼
                imageAction()
            }
            uploadButton.setOnClickListener {

                // 정보 등록이 아직 안 끝난 경우
                if(!isInfoSubmitted) {
                    Toast.makeText(this@UploadActivity, "분실물 정보를 등록해주세요!", Toast.LENGTH_SHORT).show()
                }
                else if(!isImgSubmitted) {
                    Toast.makeText(this@UploadActivity, "이미지를 등록해주세요!", Toast.LENGTH_SHORT).show()
                }
                else{
                    // 분실물의 모든 정보 등록한 경우

                    // TODO: DB에 등록된 child 개수를 받아올 수 있어야 함.
                    var rdb: DatabaseReference = Firebase.database.getReference("Lost/info") //Lost DB에 info 테이블 생성 후 참조
                    showProgressBar()

                    var childrenCount : Long = 0
                    rdb.get().addOnSuccessListener { snapshot ->
                        childrenCount = snapshot.childrenCount + 1
                        Log.i("", "child 개수 : " + childrenCount.toString());

                        val calendar = Calendar.getInstance()
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val currentTime = dateFormat.format(calendar.time)
                        val storageRef = Firebase.storage.reference
                        val imageName = "Lost_"+childrenCount.toString()+".png"
                        val imageRef = storageRef.child("Lost/info/"+imageName)
                        val uploadTask : UploadTask = imageRef.putFile(imageURI!!)
                        uploadTask.addOnSuccessListener {
                            // 이미지 업로드가 성공한 경우 호출됩니다.
                            imageRef.downloadUrl.addOnSuccessListener { uri ->
                                // 이미지 다운로드 URL이 준비된 상태에서 LostData 객체를 생성하고 업로드합니다.
                                imageURI = uri
                                val Lost = LostData(childrenCount.toInt(), dlg.binding.lostName.text.toString(), dlg.binding.findPos.text.toString(), dlg.binding.spinner.selectedItem.toString(), getImageUri(),  currentTime, password!!)
                                rdb.child(Lost.id.toString()).setValue(Lost)

                                hideProgressBar()
                                Toast.makeText(this@UploadActivity, "분실물 등록 완료!!", Toast.LENGTH_SHORT).show()
                                val intent1 = Intent(this@UploadActivity, MainActivity::class.java)
                                startActivity(intent1)
                            }.addOnFailureListener { exception ->
                                // 이미지 다운로드 URL을 얻는 데 실패한 경우 호출됩니다.
                                Log.e("FirebaseStorage", "Failed to get download URL: ${exception.message}")
                            }
                        }.addOnFailureListener { exception ->
                            // 이미지 업로드가 실패한 경우 호출됩니다.
                            Log.e("FirebaseStorage", "Failed to upload image: ${exception.message}")
                        }
                    }
                }
            }
        }
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.actiongamyen, menu)
        //menuInflater.inflate(R.menu.actionmenu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when(item.itemId){
            R.id.home ->{
                val i = Intent(this@UploadActivity, MainActivity::class.java)
                startActivity(i)
            }
        }
        return super.onOptionsItemSelected(item)
    }
}