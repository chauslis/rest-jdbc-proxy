#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/rjp}"

curl_json() {
  local title="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"

  printf '\n== %s ==\n' "$title"
  if [[ -n "$body" ]]; then
    curl -fsS -X "$method" "${BASE_URL}${path}" \
      -H 'Content-Type: application/json' \
      -d "$body"
  else
    curl -fsS -X "$method" "${BASE_URL}${path}"
  fi
  printf '\n'
}

wait_for_gateway() {
  printf 'Waiting for gateway at %s\n' "$BASE_URL"
  for _ in $(seq 1 60); do
    if curl -fsS "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
      printf 'Gateway is ready\n'
      return
    fi
    sleep 2
  done

  printf 'Gateway did not become ready\n' >&2
  exit 1
}

wait_for_task() {
  local task_id="$1"
  for _ in $(seq 1 30); do
    local status
    status="$(curl -fsS "${BASE_URL}/taskStatus/${task_id}")"
    printf 'Task %s status: %s\n' "$task_id" "$status"
    if [[ "$status" == "Task is completed" ]]; then
      return
    fi
    sleep 2
  done

  printf 'Task %s did not complete in time\n' "$task_id" >&2
  exit 1
}

wait_for_gateway

curl_json "Single stored procedure call" "POST" "/dynpst/test_pkh.proc_with_OutParam" '{
  "_rjp": {
    "connectionName": "DB1"
  },
  "params": {
    "ID": 123,
    "NAME": "test",
    "P": "INPUT p parameter value"
  }
}'

curl_json "Prepared statement call" "POST" "/dynpst/prepared_statement" '{
  "_rjp": {
    "connectionName": "DB1"
  },
  "params": {
    "AN": 7
  }
}'

curl_json "Batch request" "POST" "/batch/test_pkh.tst_function" '[
  {
    "_rjp": {
      "connectionName": "DB1"
    },
    "params": {
      "aN": "123"
    }
  },
  {
    "_rjp": {
      "connectionName": "DB1"
    },
    "params": {
      "aN": "23"
    }
  },
  {
    "_rjp": {
      "connectionName": "DB1"
    },
    "params": {
      "aN": "3"
    }
  }
]'

curl_json "Mixed-database batch request" "POST" "/batch/prepared_statement" '[
  {
    "_rjp": {
      "connectionName": "DB1"
    },
    "params": {
      "AN": 3
    }
  },
  {
    "_rjp": {
      "connectionName": "DB2"
    },
    "params": {
      "AN": 7
    }
  },
  {
    "_rjp": {
      "connectionName": "DB3"
    },
    "params": {
      "AN": 9
    }
  }
]'

printf '\n== Async execution ==\n'
task_id="$(curl -fsS -X POST "${BASE_URL}/startAsyncTask/test_pkh.tst_function" \
  -H 'Content-Type: application/json' \
  -d '[
    {
      "_rjp": {
        "connectionName": "DB1"
      },
      "params": {
        "aN": "123"
      }
    },
    {
      "_rjp": {
        "connectionName": "DB2"
      },
      "params": {
        "aN": "23"
      }
    },
    {
      "_rjp": {
        "connectionName": "DB3"
      },
      "params": {
        "aN": "3"
      }
    }
  ]' | tr -d '\r\n"')"
printf 'Started task: %s\n' "$task_id"
wait_for_task "$task_id"
curl_json "Async task result" "GET" "/taskResult/${task_id}"

curl_json "Metrics index" "GET" "/actuator/metrics"
curl_json "Gateway dynpst request counter" "GET" "/actuator/metrics/http.requests.rjp.dynpst.count"
