import sys
from pathlib import Path
from impacket import smbserver, smb3structs as smb2
from impacket.ntlm import compute_lmhash, compute_nthash

# Impacket 0.12's simple server omits related-operation TreeID/FileID propagation.
# Normalize the SMB2 inherited identifiers before its handlers; do not change the app.
for method, structure in [('smb2Create', smb2.SMB2Create), ('smb2SetInfo', smb2.SMB2SetInfo), ('smb2Close', smb2.SMB2Close)]:
    original = getattr(smbserver.SMB2Commands, method)
    def wrap(conn_id, server, packet, original=original, method=method, structure=structure):
        data = server.getConnectionData(conn_id)
        if packet['Flags'] & smb2.SMB2_FLAGS_RELATED_OPERATIONS:
            if packet['TreeID'] == 0xffffffff and '_fixture_tree' in data:
                packet['TreeID'] = data['_fixture_tree']
            if method != 'smb2Create':
                request = structure(packet['Data'])
                if request['FileID'].getData() == b'\xff' * 16 and '_fixture_file' in data:
                    request['FileID'] = data['_fixture_file']
                    packet['Data'] = request.getData()
        result = original(conn_id, server, packet)
        data = server.getConnectionData(conn_id)
        if method == 'smb2Create' and result[2] == 0:
            data['_fixture_tree'] = packet['TreeID']
            fid = result[0][0]['FileID']
            data['_fixture_file'] = fid.getData() if hasattr(fid, 'getData') else fid
            server.setConnectionData(conn_id, data)
        if result[2] != 0:
            return [smb2.SMB2Error()], None, result[2]
        return result
    setattr(smbserver.SMB2Commands, method, staticmethod(wrap))

folder = Path(sys.argv[1])
(folder / '旅行 + #100%' / '原片').mkdir(parents=True, exist_ok=True)
server = smbserver.SimpleSMBServer(listenAddress='127.0.0.1', listenPort=1445)
server.addShare('MEDIA', str(folder), 'Local integration fixture')
server.setSMB2Support(True)
server.addCredential('albumtest', 0, compute_lmhash('local-test-only'), compute_nthash('local-test-only'))
server.start()
