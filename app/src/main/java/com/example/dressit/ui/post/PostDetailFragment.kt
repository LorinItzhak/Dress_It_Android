package com.example.dressit.ui.post

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.dressit.R
import com.example.dressit.databinding.FragmentPostDetailBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.dressit.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import android.os.Bundle as AndroidBundle

@AndroidEntryPoint
class PostDetailFragment : Fragment(), OnMapReadyCallback {
    private var _binding: FragmentPostDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PostDetailViewModel by viewModels()
    private val args: PostDetailFragmentArgs by navArgs()
    private lateinit var commentAdapter: CommentAdapter
    private val userRepository = UserRepository()
    
    // מפה 
    private var mapView: MapView? = null
    private var googleMap: GoogleMap? = null
    private var postLatLng: LatLng? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCommentsRecyclerView()
        setupObservers()
        setupClickListeners()
        
        // אתחול המפה - גישה למפה דרך ה-included layout
        val mapPreviewView = requireView().findViewById<MapView>(R.id.map_preview)
        mapView = mapPreviewView
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)
        
        viewModel.loadPost(args.postId)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.apply {
            isZoomControlsEnabled = false
            isScrollGesturesEnabled = false
            isZoomGesturesEnabled = false
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
            isCompassEnabled = false
            isMapToolbarEnabled = false
        }
        
        // אם יש כבר מיקום לפוסט, מציגים אותו על המפה
        updateMapLocation()
    }
    
    private fun updateMapLocation() {
        postLatLng?.let { latLng ->
            googleMap?.clear()
            googleMap?.addMarker(MarkerOptions().position(latLng))
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            
            // מציגים את רכיב המפה - בלי להשתמש ב-visibility כרגע
            // binding.locationMapPreview.setVisibility(View.VISIBLE)
        } ?: run {
            // אם אין מיקום, מסתירים את המפה - בלי להשתמש ב-visibility כרגע
            // binding.locationMapPreview.setVisibility(View.GONE)
        }
    }

    private fun setupCommentsRecyclerView() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        
        commentAdapter = CommentAdapter(
            currentUserId = currentUserId,
            postOwnerId = "", // יעודכן כשהפוסט יטען
            onDeleteClick = { comment ->
                viewModel.deleteComment(args.postId, comment.id)
            }
        )
        
        binding.commentsRecyclerView.apply {
            adapter = commentAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
        }
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }
        
        // ניווט לפרופיל המשתמש כאשר לוחצים על שם המשתמש
        binding.userName.setOnClickListener {
            viewModel.post.value?.let { post ->
                if (post.userId != FirebaseAuth.getInstance().currentUser?.uid) {
                    val action = PostDetailFragmentDirections.actionPostDetailToProfileFragment(post.userId)
                    findNavController().navigate(action)
                } else {
                    // אם זה המשתמש הנוכחי, נווט לטאב של הפרופיל
                    findNavController().navigate(R.id.profileFragment)
                }
            }
        }
        
        binding.deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
        
        binding.editButton.setOnClickListener {
            viewModel.post.value?.let { post ->
                val action = PostDetailFragmentDirections.actionPostDetailToEditPost(post.id)
                findNavController().navigate(action)
            }
        }
        
        binding.contactButton.setOnClickListener {
            // בדיקה אם המשתמש הנוכחי הוא בעל הפוסט
            viewModel.post.value?.let { post ->
                if (viewModel.isCurrentUserPostOwner(post)) {
                    // אם המשתמש הוא בעל הפוסט, מציגים הודעה שלא ניתן להשכיר שמלות שהמשתמש עצמו פרסם
                    Snackbar.make(binding.root, "לא ניתן להשכיר שמלות שאתה פרסמת", Snackbar.LENGTH_SHORT).show()
                } else {
                    // TODO: Implement contact functionality
                    Snackbar.make(binding.root, "פונקציונליות יצירת קשר תיושם בקרוב", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
        
        // לייק
        binding.likeButton.setOnClickListener {
            viewModel.toggleLike(args.postId)
        }
        
        // תגובה
        binding.commentButton.setOnClickListener {
            binding.commentInput.requestFocus()
        }
        
        // שמירה
        binding.saveButton.setOnClickListener {
            viewModel.toggleSave(args.postId)
        }
        
        // שליחת תגובה
        binding.sendCommentButton.setOnClickListener {
            val commentText = binding.commentInput.text.toString().trim()
            if (commentText.isNotEmpty()) {
                viewModel.addComment(args.postId, commentText)
                binding.commentInput.text.clear()
            }
        }
    }

    private fun setupObservers() {
        viewModel.post.observe(viewLifecycleOwner) { post ->
            post?.let {
                binding.apply {
                    title.text = post.title
                    userName.text = post.userName
                    description.text = post.description
                    timestamp.text = formatTimestamp(post.timestamp)
                    
                    // טעינת תמונת הפוסט
                    Glide.with(requireContext())
                        .load(post.imageUrl)
                        .placeholder(R.drawable.bg_image_placeholder)
                        .error(R.drawable.ic_error_placeholder)
                        .into(postImage)
                    
                    // טעינת תמונת פרופיל של המשתמש
                    loadUserProfileImage(post.userId, userName)
                    
                    // הצגת מחיר השכרה
                    val formattedPrice = formatPrice(post.rentalPrice, post.currency)
                    rentalPrice.text = formattedPrice
                    
                    // עדכון מידע מיקום
                    if (post.latitude != null && post.longitude != null) {
                        postLatLng = LatLng(post.latitude, post.longitude)
                        updateMapLocation()
                        
                        try {
                            val geocoder = android.location.Geocoder(requireContext(), java.util.Locale.getDefault())
                            val addresses = geocoder.getFromLocation(post.latitude, post.longitude, 1)
                            if (addresses != null && addresses.isNotEmpty()) {
                                val address = addresses[0]
                                val formattedAddress = StringBuilder()
                                
                                // בניית כתובת מפורטת ככל האפשר
                                if (address.thoroughfare != null) {
                                    formattedAddress.append(address.thoroughfare)
                                    
                                    // מספר בית אם קיים
                                    if (address.subThoroughfare != null) {
                                        formattedAddress.append(" ").append(address.subThoroughfare)
                                    }
                                }
                                
                                // הוספת העיר/יישוב
                                if (address.locality != null) {
                                    if (formattedAddress.isNotEmpty()) {
                                        formattedAddress.append(", ")
                                    }
                                    formattedAddress.append(address.locality)
                                } else if (address.subAdminArea != null) {
                                    if (formattedAddress.isNotEmpty()) {
                                        formattedAddress.append(", ")
                                    }
                                    formattedAddress.append(address.subAdminArea)
                                } else if (address.adminArea != null) {
                                    if (formattedAddress.isNotEmpty()) {
                                        formattedAddress.append(", ")
                                    }
                                    formattedAddress.append(address.adminArea)
                                }
                                
                                // אם לא הצלחנו לבנות כתובת משום שאין מספיק נתונים, ננסה את הכתובת הכללית
                                if (formattedAddress.isEmpty() && address.getAddressLine(0) != null) {
                                    formattedAddress.append(address.getAddressLine(0))
                                }
                                
                                // אם יש לנו כתובת כלשהי - נציג אותה
                                if (formattedAddress.isNotEmpty()) {
                                    pickupLocation.text = "מיקום איסוף: $formattedAddress"
                                    
                                    // נדלג על עדכון טקסט המפה עד שנתקן את הבעיה
                                } else {
                                    // אם אין כתובת מפורטת, נשתמש בשם האיזור או העיר הכי קרובים
                                    val fallbackAddress = when {
                                        address.locality != null -> address.locality
                                        address.subAdminArea != null -> address.subAdminArea
                                        address.adminArea != null -> address.adminArea
                                        else -> null
                                    }
                                    
                                    if (fallbackAddress != null) {
                                        pickupLocation.text = "מיקום איסוף: $fallbackAddress"
                                    } else {
                                        pickupLocation.text = "מיקום איסוף זמין - פנה למוכר לפרטים"
                                    }
                                }
                                
                                // הגדרת האפשרות ללחוץ על המפה כדי לפתוח אותה באפליקציה חיצונית
                                val mapClickListener = View.OnClickListener {
                                    val mapUrl = "https://maps.google.com/maps?q=${post.latitude},${post.longitude}"
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(mapUrl))
                                    startActivity(intent)
                                }
                                
                                // הגדרת לחיצה על המפה ועל כתובת האיסוף - נדלג על כך כרגע
                                // locationMapPreview.setOnClickListener(mapClickListener)
                                // pickupLocation.setOnClickListener(mapClickListener)
                                
                                // binding.pickupLocation.setVisibility(View.VISIBLE)
                            } else {
                                binding.pickupLocation.text = "מיקום איסוף זמין - פנה למוכר לפרטים"
                                // binding.pickupLocation.setVisibility(View.VISIBLE)
                            }
                        } catch (e: Exception) {
                            // במקרה של שגיאה, נראה הודעה ידידותית ולא את הקואורדינטות
                            binding.pickupLocation.text = "מיקום איסוף זמין - פנה למוכר לפרטים"
                            // binding.pickupLocation.setVisibility(View.VISIBLE)
                        }
                    } else {
                        // אם אין מיקום, מסתירים את ה-TextView והמפה
                        // binding.pickupLocation.setVisibility(View.GONE)
                        // binding.locationMapPreview.setVisibility(View.GONE)
                    }
                    
                    // עדכון מצב הלייק
                    updateLikeButton(viewModel.isPostLikedByCurrentUser(post))
                    
                    // עדכון מצב השמירה
                    updateSaveButton(viewModel.isPostSavedByCurrentUser(post))
                    
                    // עדכון רשימת התגובות
                    commentAdapter = CommentAdapter(
                        currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                        postOwnerId = post.userId,
                        onDeleteClick = { comment ->
                            viewModel.deleteComment(post.id, comment.id)
                        }
                    )
                    commentsRecyclerView.adapter = commentAdapter
                    commentAdapter.submitList(post.comments)
                    
                    // בדיקה אם המשתמש הנוכחי הוא בעל הפוסט
                    val isOwner = viewModel.isCurrentUserPostOwner(post)
                    ownerActions.isVisible = isOwner
                    contactButton.isVisible = !isOwner
                }
            }
        }

        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
            binding.postImage.isVisible = !isLoading
            binding.title.isVisible = !isLoading
            binding.rentalPrice.isVisible = !isLoading
            binding.pickupLocation.isVisible = !isLoading && viewModel.post.value?.latitude != null
            binding.userName.isVisible = !isLoading
            binding.description.isVisible = !isLoading
            binding.timestamp.isVisible = !isLoading
            binding.interactionLayout.isVisible = !isLoading
            binding.commentsSection.isVisible = !isLoading
            binding.ownerActions.isVisible = !isLoading && viewModel.post.value?.let { viewModel.isCurrentUserPostOwner(it) } ?: false
            binding.contactButton.isVisible = !isLoading && viewModel.post.value?.let { !viewModel.isCurrentUserPostOwner(it) } ?: false
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
            }
        }

        viewModel.postDeleted.observe(viewLifecycleOwner) { isDeleted ->
            if (isDeleted) {
                findNavController().navigateUp()
            }
        }
        
        viewModel.commentAdded.observe(viewLifecycleOwner) { isAdded ->
            if (isAdded) {
                Snackbar.make(binding.root, "התגובה נוספה בהצלחה", Snackbar.LENGTH_SHORT).show()
            }
        }
        
        viewModel.commentDeleted.observe(viewLifecycleOwner) { isDeleted ->
            if (isDeleted) {
                Snackbar.make(binding.root, "התגובה נמחקה בהצלחה", Snackbar.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateLikeButton(isLiked: Boolean) {
        if (isLiked) {
            binding.likeButton.setImageResource(R.drawable.ic_heart_filled)
        } else {
            binding.likeButton.setImageResource(R.drawable.ic_heart_outline)
        }
    }
    
    private fun updateSaveButton(isSaved: Boolean) {
        if (isSaved) {
            binding.saveButton.setImageResource(R.drawable.ic_bookmark_filled)
        } else {
            binding.saveButton.setImageResource(R.drawable.ic_bookmark_outline)
        }
    }
    
    private fun showDeleteConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("מחיקת פוסט")
            .setMessage("האם את/ה בטוח/ה שברצונך למחוק את הפוסט?")
            .setNegativeButton("ביטול") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("מחק") { _, _ ->
                viewModel.deletePost()
            }
            .show()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    private fun formatPrice(price: Double, currency: String): String {
        val format = NumberFormat.getCurrencyInstance(Locale("he", "IL"))
        format.currency = java.util.Currency.getInstance(currency)
        return format.format(price) + " להשכרה"
    }

    // פונקציה שטוענת את תמונת הפרופיל של המשתמש
    private fun loadUserProfileImage(userId: String, userNameView: android.widget.TextView) {
        if (userId.isBlank()) return
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    userRepository.getUserById(userId)
                }
                
                if (user != null && user.profilePicture.isNotEmpty()) {
                    // טעינת תמונת הפרופיל רק אם קיימת
                    withContext(Dispatchers.Main) {
                        Glide.with(requireContext())
                            .load(user.profilePicture)
                            .circleCrop() // עיגול של התמונה
                            .placeholder(R.drawable.ic_profile) // התמונה הזמנית עד שהתמונה האמיתית נטענת
                            .error(R.drawable.ic_profile) // התמונה שתוצג במקרה של שגיאה
                            .into(object : com.bumptech.glide.request.target.CustomTarget<Drawable>() {
                                override fun onResourceReady(
                                    resource: Drawable,
                                    transition: com.bumptech.glide.request.transition.Transition<in Drawable>?
                                ) {
                                    // הגדרת גודל האייקון
                                    resource.setBounds(0, 0, 80, 80)
                                    
                                    // שמירה על הריווח המקורי
                                    val drawables = userNameView.compoundDrawables
                                    userNameView.setCompoundDrawables(resource, drawables[1], drawables[2], drawables[3])
                                }

                                override fun onLoadCleared(placeholder: Drawable?) {
                                    // אין צורך לעשות כלום
                                }
                            })
                    }
                } else {
                    Log.d("PostDetailFragment", "No profile picture for user $userId or user not found")
                }
            } catch (e: Exception) {
                Log.e("PostDetailFragment", "Error loading profile picture for user $userId", e)
            }
        }
    }

    // מחזור חיי MapView
    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView?.onDestroy()
        mapView = null
        _binding = null
    }
} 