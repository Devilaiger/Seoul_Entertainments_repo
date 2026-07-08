import urllib.request
import ssl
import json
import re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Origin": "https://gofile.io",
    "Referer": "https://gofile.io/"
}

try:
    # Step 1: Create Account
    req1 = urllib.request.Request("https://api.gofile.io/accounts", data=b"", headers=headers, method="POST")
    with urllib.request.urlopen(req1, context=ctx) as res1:
        acc_data = json.loads(res1.read().decode('utf-8'))
        print("Account Response:", json.dumps(acc_data, indent=2))
        token = acc_data.get("data", {}).get("token")
        print("Token:", token)
        
    # Step 2: Fetch Website Token (wt)
    # We will fetch gofile's main page to see if we can find wt in any js files or config
    req2 = urllib.request.Request("https://gofile.io/dist/js/config.js", headers=headers)
    with urllib.request.urlopen(req2, context=ctx) as res2:
        config_text = res2.read().decode('utf-8')
        print("Config JS snippet:", config_text[:200])
        # Find wt
        match = re.search(r"appdata\.wt\s*=\s*['\"]([^'\"]+)['\"]", config_text)
        wt = match.group(1) if match else None
        print("Website Token (wt):", wt)
        
    # Step 3: Fetch folder contents
    folder_id = "cguHBl"
    url = f"https://api.gofile.io/contents/{folder_id}?contentFilter=&page=1&pageSize=1000&sortField=name&sortDirection=1"
    
    api_headers = {
        **headers,
        "Authorization": f"Bearer {token}",
        "X-Website-Token": wt,
        "Referer": f"https://gofile.io/d/{folder_id}"
    }
    
    req3 = urllib.request.Request(url, headers=api_headers)
    with urllib.request.urlopen(req3, context=ctx) as res3:
        contents = json.loads(res3.read().decode('utf-8'))
        print("Contents Response:", json.dumps(contents, indent=2))
        
except Exception as e:
    print("Error:", e)
