import { it,expect } from 'vitest'
import { createApp,nextTick } from 'vue'
import ChatBox from './ChatBox.vue'
function mount(props){const host=document.createElement('div');document.body.append(host);const sent=[];const app=createApp(ChatBox,{...props,onSend:v=>sent.push(v)});app.mount(host);return {host,sent,unmount:()=>{app.unmount();host.remove()}}}
async function input(host,text){const el=host.querySelector('.message-input');el.value=text;el.dispatchEvent(new Event('input'));await nextTick();host.querySelector('form').dispatchEvent(new Event('submit',{cancelable:true}));await nextTick()}
it('trims text and never emits empty messages',async()=>{const w=mount({messages:[],disabled:false});await input(w.host,'   ');expect(w.sent).toHaveLength(0);await input(w.host,' hello ');expect(w.sent).toEqual(['hello']);w.unmount()})
it('does not send disabled messages',async()=>{const w=mount({messages:[],disabled:true});await input(w.host,'blocked');expect(w.sent).toHaveLength(0);w.unmount()})
it('renders malicious text safely',()=>{const w=mount({messages:[{messageId:'one',senderId:'peer',text:'<img src=x onerror=alert(1)>'}],guestId:'me'});expect(w.host.querySelector('img')).toBeNull();expect(w.host.textContent).toContain('<img src=x onerror=alert(1)>');w.unmount()})
