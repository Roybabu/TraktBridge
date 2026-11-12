package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class EncryptedBackupTest {
 @Test fun roundTrip() {
  val password="test-password-123".toCharArray()
  val message="viewing history • 日本語"
  val encrypted=EncryptedBackup.encrypt(message,password)
  assertEquals(message,EncryptedBackup.decrypt(encrypted,password))
  assertFalse(encrypted.contentEquals(EncryptedBackup.encrypt(message,password)))
 }
 @Test fun tamperingRejected() {
  val password="test-password-123".toCharArray()
  val data=EncryptedBackup.encrypt("history",password)
  data[data.lastIndex]=(data.last().toInt() xor 1).toByte()
  assertThrows(javax.crypto.AEADBadTagException::class.java) { EncryptedBackup.decrypt(data,password) }
 }
 @Test fun wrongPasswordRejected() {
  val data=EncryptedBackup.encrypt("history","test-password-123".toCharArray())
  assertThrows(javax.crypto.AEADBadTagException::class.java) { EncryptedBackup.decrypt(data,"wrong-password-456".toCharArray()) }
 }
}
