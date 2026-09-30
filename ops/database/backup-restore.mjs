import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync, existsSync, mkdirSync, openSync, closeSync, createReadStream } from 'node:fs'
import { createInterface } from 'node:readline'
import { dirname, resolve } from 'node:path'

// Usage: node backup-restore.mjs backup|restore-check database backup.sql
// Credentials come only from a protected MySQL option file, never command-line passwords.
const [mode, database, filename] = process.argv.slice(2)
if (!['backup','restore-check'].includes(mode) || !/^[a-zA-Z0-9_]+$/.test(database ?? '') || !filename)
  throw new Error('Usage: backup|restore-check database backup.sql')
const options = process.env.MYSQL_DEFAULTS_FILE
if (!options || !existsSync(options)) throw new Error('MYSQL_DEFAULTS_FILE must reference a protected [client] option file')
const mysql = process.env.MYSQL_BIN || 'mysql'
const dump = process.env.MYSQLDUMP_BIN || 'mysqldump'
const file = resolve(filename)
const connection = [`--defaults-file=${resolve(options)}`]
async function hash(path) {
  const digest=createHash('sha256')
  for await (const chunk of createReadStream(path)) digest.update(chunk)
  return digest.digest('hex')
}
async function canonicalHash(path) {
  const digest=createHash('sha256')
  for await(const line of createInterface({input:createReadStream(path),crlfDelay:Infinity})) {
    // SHOW CREATE adds redundant CHARACTER SET on restored columns. A named
    // collation already determines that charset. Normalize only column DDL,
    // preserving every data row, index, constraint and other table option.
    const canonical=line.startsWith('  `')
      ? line.replace(/ CHARACTER SET (\w+) COLLATE \1_/g,' COLLATE $1_') : line
    digest.update(canonical+'\n')
  }
  return digest.digest('hex')
}
const query = sql => {
  const result = spawnSync(mysql,[...connection,'--batch','--skip-column-names','--execute',sql],{encoding:'utf8'})
  if (result.status !== 0) throw new Error(`MySQL command failed (exit ${result.status}); inspect protected server logs`)
  return result.stdout.trim()
}
function snapshot(db, path) {
  const fd=openSync(path,'wx',0o600)
  try {
    const result=spawnSync(dump,[...connection,'--single-transaction','--quick','--skip-lock-tables',
      '--routines','--events','--triggers','--hex-blob','--no-tablespaces','--set-gtid-purged=OFF',
      '--skip-comments','--skip-dump-date','--order-by-primary',db],{stdio:['ignore',fd,'pipe']})
    if (result.status!==0) throw new Error(`Backup failed (exit ${result.status}); incomplete file must not be restored`)
  } finally { closeSync(fd) }
}
const started=performance.now()
if (mode==='backup') {
  mkdirSync(dirname(file),{recursive:true})
  snapshot(database,file)
  const checksum=await hash(file)
  writeFileSync(`${file}.sha256`,`${checksum}\n`,{flag:'wx',mode:0o600})
  console.log(JSON.stringify({operation:mode,database,sha256:checksum,elapsedMs:Math.round(performance.now()-started)}))
} else {
  if (!/^jsj_restore_[a-zA-Z0-9_]+$/.test(database)) throw new Error('Restore targets must start with jsj_restore_ and must not exist')
  const checksum=await hash(file)
  if (checksum!==readFileSync(`${file}.sha256`,'utf8').trim()) throw new Error('Backup checksum mismatch')
  for await (const line of createInterface({input:createReadStream(file),crlfDelay:Infinity})) {
    if (/^\s*(?:USE\s|CREATE\s+DATABASE|DROP\s+DATABASE)/i.test(line)) throw new Error('Backup must not select or modify another database')
  }
  query(`CREATE DATABASE \`${database}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`)
  const fd=openSync(file,'r')
  try {
    const result=spawnSync(mysql,[...connection,database],{stdio:[fd,'ignore','pipe']})
    if (result.status!==0) throw new Error('Restore failed; leave scratch database intact for diagnosis')
  } finally { closeSync(fd) }
  const restored=`${file}.restored.sql`
  snapshot(database,restored)
  const restoredChecksum=await hash(restored)
  const schemaDataChecksum=await canonicalHash(file)
  if (schemaDataChecksum!==await canonicalHash(restored)) throw new Error('Restored schema/data differs from the backup; inspect both protected dumps')
  const migrations=query(`SELECT COUNT(*) FROM \`${database}\`.flyway_schema_history WHERE success=0`)
  if(migrations!=='0') throw new Error('Restored database contains failed migrations')
  const constraints=query(`SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema='${database}'`)
  if(Number(constraints)<4) throw new Error('Restored database is missing foreign keys')
  console.log(JSON.stringify({operation:mode,database,backupSha256:checksum,restoredSha256:restoredChecksum,
    schemaDataSha256:schemaDataChecksum,foreignKeys:Number(constraints),elapsedMs:Math.round(performance.now()-started)}))
}
