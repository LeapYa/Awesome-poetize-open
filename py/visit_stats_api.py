from fastapi import FastAPI, Request, HTTPException, Depends
import mysql.connector
import datetime
import os
import yaml
import re
import traceback
from auth_decorator import admin_required  # 导入管理员权限装饰器

# 从环境变量或application.yml读取数据库配置
def get_db_config():
    try:
        # 首先尝试从环境变量获取数据库配置
        if os.environ.get('MYSQL_HOST'):
            print("从环境变量读取数据库配置")
            return {
                'host': os.environ.get('MYSQL_HOST', 'localhost'),
                'user': os.environ.get('MYSQL_USER', 'root'),
                'password': os.environ.get('MYSQL_PASSWORD', '123456'),
                'database': os.environ.get('MYSQL_DATABASE', 'poetize'),
                'port': os.environ.get('MYSQL_PORT', '3306')
            }
            
        print("未找到环境变量配置，尝试从application.yml读取")
        # 获取application.yml文件路径
        yml_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 
                               'poetize-server', 'poetry-web', 'src', 'main', 'resources', 'application.yml')
        
        # 读取application.yml文件
        with open(yml_path, 'r', encoding='utf-8') as f:
            config = yaml.safe_load(f)
            
        # 从config中提取数据库配置
        db_config = config.get('spring', {}).get('datasource', {})
        db_url = db_config.get('url', '')
        
        # 从JDBC URL中提取主机名和数据库名
        host_match = re.search(r'jdbc:mysql://([^:/]+)', db_url)
        db_match = re.search(r'jdbc:mysql://[^/]+/([^?]+)', db_url)
        
        host = host_match.group(1) if host_match else 'localhost'
        database = db_match.group(1) if db_match else 'poetize'
        
        # 确保所有配置项都是字符串类型
        return {
            'host': str(host),
            'user': str(db_config.get('username', 'root')),
            'password': str(db_config.get('password', '123456')),
            'database': str(database),
            'port': '3306'  # 添加默认端口
        }
    except Exception as e:
        print(f"读取数据库配置失败: {str(e)}")
        # 返回默认配置
        return {
            'host': 'localhost',
            'user': 'root',
            'password': '123456',
            'database': 'poetize',
            'port': '3306'
        }

# 获取数据库连接
def get_db_connection():
    db_config = get_db_config()
    return mysql.connector.connect(**db_config)

# 注册访问统计API路由
def register_visit_stats_api(app: FastAPI):
    @app.get('/webInfo/getDailyVisitStats')
    async def get_daily_visit_stats(request: Request, _: bool = Depends(admin_required)):
        """获取每日访问量统计数据"""
        print("收到获取每日访问量统计请求")
        try:
            # 获取查询参数
            days_str = request.query_params.get('days', '30')
            try:
                days = int(days_str)
                if days <= 0:
                    days = 30
                elif days > 365:
                    days = 365  # 限制最大查询范围
            except:
                days = 30
            
            print(f"查询天数: {days}")
                
            conn = None
            cursor = None
            try:
                conn = get_db_connection()
                cursor = conn.cursor(dictionary=True)
                
                # 简化查询，避免可能的兼容性问题
                query = f"""
                SELECT 
                    DATE_FORMAT(create_time, '%Y-%m-%d') as visit_date,
                    COUNT(DISTINCT ip) as unique_visits,
                    COUNT(*) as total_visits
                FROM history_info
                WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL {days} DAY)
                GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')
                ORDER BY visit_date
                """
                
                print(f"执行SQL: {query}")
                
                cursor.execute(query)
                daily_stats = cursor.fetchall()
                
                print(f"查询结果数量: {len(daily_stats) if daily_stats else 0}")
                
                # 处理空结果情况
                if not daily_stats:
                    print("没有找到访问统计数据")
                    return {"code": 200, "message": "Success", "data": []}
                
                # 处理结果
                result_stats = []
                total_unique_visits = 0
                
                for stat in daily_stats:
                    new_stat = {}
                    
                    # 安全地获取每个字段，处理NULL值
                    for key in stat:
                        if stat[key] is None:
                            new_stat[key] = "未知"
                        else:
                            new_stat[key] = stat[key]
                    
                    # 确保unique_visits是数字类型用于计算
                    if new_stat['unique_visits'] != "未知":
                        total_unique_visits += float(new_stat['unique_visits'])
                    
                    result_stats.append(new_stat)
                
                # 计算平均值
                avg_unique_visits = 0
                if result_stats:
                    avg_unique_visits = round(total_unique_visits / len(result_stats), 2)
                
                # 添加平均值到结果中
                for stat in result_stats:
                    stat['avg_unique_visits'] = avg_unique_visits
                
                print(f"处理后的结果数量: {len(result_stats)}")
                return {"code": 200, "message": "Success", "data": result_stats}
            
            except mysql.connector.Error as db_err:
                print(f"数据库错误: {str(db_err)}")
                raise HTTPException(status_code=500, detail={"code": 500, "message": f"数据库错误: {str(db_err)}"})
            
            finally:
                if cursor:
                    cursor.close()
                if conn:
                    conn.close()
        
        except HTTPException:
            raise
        except Exception as e:
            print(f"获取每日访问量统计出错: {str(e)}")
            traceback.print_exc()
            raise HTTPException(status_code=500, detail={"code": 500, "message": f"获取每日访问量统计失败: {str(e)}"})

# 当直接运行此文件时的测试代码
if __name__ == '__main__':
    import uvicorn
    app = FastAPI()
    register_visit_stats_api(app)
    uvicorn.run(app, host="0.0.0.0", port=5002, debug=True) 