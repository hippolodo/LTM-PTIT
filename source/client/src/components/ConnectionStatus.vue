<script setup>
defineProps({status:String})
const labels={IDLE:'Sẵn sàng',WAITING:'Đang tìm người trò chuyện…',MATCHED:'Đã ghép cặp · chat sẵn sàng',CONNECTING:'Đã ghép cặp · đang kết nối media…',CONNECTED:'Kết nối WebRTC đã sẵn sàng',PARTNER_LEFT:'Đối tác đã rời đi · nhấn Start để tìm tiếp',ERROR:'Kết nối gặp lỗi'}
</script><template><span class="status" role="status"><i :class="status.toLowerCase()"></i>{{ labels[status] || status }}</span></template>
